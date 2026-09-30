package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulingAiConstraint;
import com.chronos.education.scheduling.model.SchedulingAiLockedEntry;
import com.chronos.education.scheduling.model.SchedulingAiOfferingConstraint;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiSoftPriority;
import com.chronos.education.scheduling.model.SchedulingAiWeekRule;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic first-stage parser. It only emits IDs loaded from trusted
 * repositories and deliberately turns missing/ambiguous references into
 * clarification items.
 */
@Service
public class SchedulingAiRequirementParser {
	private static final Pattern PERIOD_PATTERN =
			Pattern.compile("第?([一二三四五六七八九十\\d]+)节");
	private static final Pattern OFFERING_BLOCK_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(.+?)\\s*(?:尽量|优先|希望|最好)连堂(?:上课|排课|排)?");
	private static final Pattern WEEK_RULE_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(.+?)\\s*(?:(?:仅|安排在|限定为|按)?"
					+ "(单周|奇数周|双周|偶数周|单双周|每周)"
					+ "(?:第?(\\d+)周(?:至|到|[-—])第?(\\d+)周)?"
					+ "|第?(\\d+)周(?:至|到|[-—])第?(\\d+)周)\\s*");
	private static final Pattern LOCK_ENTRY_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(?:本次)?(?:临时)?保留(?:当前|现有)?"
					+ "(?:课表条目|课表项|条目)\\s*[:：#]?\\s*([A-Za-z0-9_-]+)");
	private static final Pattern LOCK_ENTRY_SLOT_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(?:本次)?(?:临时)?保留(?:当前|现有)?\\s*(.+?)\\s*"
					+ "((?:星期|周)[一二三四五六日天])\\s*第?([一二三四五六七八九十\\d]+)节");
	private static final Pattern LOCK_ENTRY_OFFERING_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(?:本次)?(?:临时)?保留(?:当前|现有)?\\s*"
					+ "(.+?)(?:的)?(?:课程|教学任务|课表|排课)");
	private static final Pattern LOCK_ENTRY_ALIAS_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(?:本次)?(?:临时)?保留(?:当前|现有)?\\s*(.+)");
	private static final Pattern SOFT_PRIORITY_PATTERN = Pattern.compile(
			"(?:请|帮我)?\\s*(.+?)\\s*(?:尽量|优先|希望|最好)?\\s*"
					+ "(减少跨校区|减少校区切换|避免跨校区|跨校区切换少|"
					+ "减少空档|减少教师空档|避免空档|"
					+ "同一天集中|尽量同日集中|集中在同一天|同日集中)(?:排课)?");
	private static final Pattern SUPPORTED_TEACHER_SLOT = Pattern.compile(
			"(?:请|帮我|给)?\\s*TEACHER\\s*(?:在|于)?"
					+ "(?:星期[一二三四五六日天]|周[一二三四五六日天])\\s*"
					+ "第?[一二三四五六七八九十\\d]+节\\s*"
					+ "(?:不能|不可|禁止|禁排|不排|尽量|优先|希望|最好)"
					+ "(?:上课|排课|排|安排)?");
	private static final Pattern SUPPORTED_TEACHER_SEGMENT = Pattern.compile(
			"(?:请|帮我|给)?\\s*TEACHER\\s*(?:在|于)?"
					+ "(?:星期[一二三四五六日天]|周[一二三四五六日天])\\s*"
					+ "(?:上午|下午|晚上)\\s*"
					+ "(?:不能|不可|禁止|禁排|不排|尽量|优先|希望|最好)"
					+ "(?:上课|排课|排|安排)?");
	private static final Map<String, Integer> DAYS = Map.ofEntries(
			Map.entry("星期一", 1), Map.entry("周一", 1),
			Map.entry("星期二", 2), Map.entry("周二", 2),
			Map.entry("星期三", 3), Map.entry("周三", 3),
			Map.entry("星期四", 4), Map.entry("周四", 4),
			Map.entry("星期五", 5), Map.entry("周五", 5),
			Map.entry("星期六", 6), Map.entry("周六", 6),
			Map.entry("星期日", 7), Map.entry("星期天", 7),
			Map.entry("周日", 7));

	private final AcademicTermRepository terms;
	private final CourseOfferingRepository offerings;
	private final TeacherAcademicProfileRepository teachers;
	private final EducationDataScopeService dataScopes;
	private final SchedulingAiModelClassifier modelClassifier;
	private final SchedulingAgentTimetableService timetable;
	private final ScheduleEntryRepository scheduleEntries;

	@Autowired
	public SchedulingAiRequirementParser(
			AcademicTermRepository terms,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			EducationDataScopeService dataScopes,
			SchedulingAiModelClassifier modelClassifier,
			SchedulingAgentTimetableService timetable,
			ScheduleEntryRepository scheduleEntries) {
		this.terms = terms;
		this.offerings = offerings;
		this.teachers = teachers;
		this.dataScopes = dataScopes;
		this.modelClassifier = modelClassifier;
		this.timetable = timetable;
		this.scheduleEntries = scheduleEntries;
	}

	SchedulingAiRequirementParser(AcademicTermRepository terms,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			EducationDataScopeService dataScopes,
			SchedulingAiModelClassifier modelClassifier) {
		this(terms, offerings, teachers, dataScopes, modelClassifier, null, null);
	}

	SchedulingAiRequirementParser(AcademicTermRepository terms,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			EducationDataScopeService dataScopes,
			SchedulingAiModelClassifier modelClassifier,
			SchedulingAgentTimetableService timetable) {
		this(terms, offerings, teachers, dataScopes, modelClassifier, timetable, null);
	}

	SchedulingAiRequirementParser(AcademicTermRepository terms,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			EducationDataScopeService dataScopes) {
		this(terms, offerings, teachers, dataScopes, null, null, null);
	}

	public ParsedRequirement parse(SchedulingAiRunRequest request, String username) {
		terms.findByTermCode(request.semesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		EducationDataScope scope = dataScopes.resolve(username);
		Set<String> selected = validateSelectedOfferings(request, scope);
		List<String> clarifications = new java.util.ArrayList<>();
		List<SchedulingAiConstraint> constraints = new java.util.ArrayList<>();
		List<SchedulingAiOfferingConstraint> offeringConstraints = new java.util.ArrayList<>();
		List<SchedulingAiWeekRule> weekRules = new java.util.ArrayList<>();
		List<SchedulingAiLockedEntry> lockedEntries = new java.util.ArrayList<>();
		List<SchedulingAiSoftPriority> softPriorities = new java.util.ArrayList<>();
		List<String> unsupported = new java.util.ArrayList<>();
		List<String> unresolvedClauses = new java.util.ArrayList<>();
		String text = request.requestText();
		validateMentionedSemester(request, text, clarifications);
		validateMentionedMode(request, text, clarifications);
		List<SchedulingAiModelClassifier.Clause> clauses = modelClassifier == null
				? java.util.Arrays.stream(text.split("[；;。，,\\n]+"))
						.map(String::strip).filter(value -> !value.isEmpty())
						.map(value -> new SchedulingAiModelClassifier.Clause(value, "DETERMINISTIC"))
						.toList()
				: modelClassifier.classify(text);
		for (SchedulingAiModelClassifier.Clause clause : clauses) {
			String rule = clause.text();
			if ("UNSUPPORTED".equals(clause.classification())) {
				unsupported.add("模型无法确认该需求：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			if (!"DETERMINISTIC".equals(clause.classification())
					&& !("GENERATION".equals(clause.classification()) && isGenerationInstruction(rule))
					&& !("TEACHER_SLOT".equals(clause.classification())
							&& !isGenerationInstruction(rule)
							&& !OFFERING_BLOCK_PATTERN.matcher(rule).matches())
					&& !("OFFERING_BLOCK".equals(clause.classification())
							&& OFFERING_BLOCK_PATTERN.matcher(rule).matches())
					&& !("WEEK_RULE".equals(clause.classification())
							&& WEEK_RULE_PATTERN.matcher(rule).matches())
					&& !("LOCK_ENTRY".equals(clause.classification())
							&& isLockEntryForm(rule))
					&& !("TEACHER_PRIORITY".equals(clause.classification())
							&& SOFT_PRIORITY_PATTERN.matcher(rule).matches())) {
				unsupported.add("模型分类与服务端规则不一致：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			if (rule.isEmpty() || isGenerationInstruction(rule)) {
				continue;
			}
			if ("WEEK_RULE".equals(clause.classification())
					|| WEEK_RULE_PATTERN.matcher(rule).matches()) {
				parseWeekRule(request, scope, selected, rule, weekRules, clarifications,
						unsupported, unresolvedClauses);
				continue;
			}
			if ("LOCK_ENTRY".equals(clause.classification()) || isLockEntryForm(rule)) {
				parseLockedEntry(request, scope, selected, rule, lockedEntries,
						clarifications, unsupported, unresolvedClauses);
				continue;
			}
			if ("TEACHER_PRIORITY".equals(clause.classification())
					|| SOFT_PRIORITY_PATTERN.matcher(rule).matches()) {
				parseSoftPriority(rule, scope, softPriorities, clarifications,
						unsupported, unresolvedClauses);
				continue;
			}
			Matcher block = OFFERING_BLOCK_PATTERN.matcher(rule);
			if (block.matches()) {
				String course = block.group(1).strip();
				List<CourseOffering> matched = dataScopes.visibleOfferings(scope,
						offerings.findBySemesterCodeOrderByOfferingCode(request.semesterCode()))
						.stream().filter(offering -> "ACTIVE".equals(offering.getStatus()))
						.filter(offering -> "GLOBAL".equals(request.mode())
								|| selected.contains(offering.getId()))
						.filter(offering -> course.equals(offering.getCourseCode())
								|| course.equals(offering.getCourseName()))
						.toList();
				if (matched.isEmpty() || matched.stream().map(CourseOffering::getCourseCode)
						.distinct().count() != 1
						|| matched.stream().anyMatch(offering ->
								offering.getWeeklyLessons() == null || offering.getWeeklyLessons() < 2)) {
					clarifications.add("请指定排课范围内唯一且每周至少两节的课程：" + rule);
					unresolvedClauses.add(rule);
					continue;
				}
				for (CourseOffering offering : matched) {
					if (offeringConstraints.stream().noneMatch(existing ->
							existing.offeringId().equals(offering.getId()))) {
						offeringConstraints.add(new SchedulingAiOfferingConstraint(
								offering.getId(), offering.getCourseName(), 2, rule));
					}
				}
				continue;
			}
			boolean forbidden = containsAny(rule, "不能", "不可", "禁止", "禁排", "不排");
			boolean preferred = containsAny(rule, "尽量", "优先", "希望", "最好");
			if (!forbidden && !preferred) {
				unsupported.add("暂不支持该需求：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			if (forbidden && preferred) {
				clarifications.add("同一条规则同时包含禁排和偏好，请拆开描述：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			if (DAYS.entrySet().stream().filter(entry -> rule.contains(entry.getKey()))
					.map(Map.Entry::getValue).distinct().count() > 1
					|| PERIOD_PATTERN.matcher(rule).results().count() > 1) {
				unsupported.add("一条规则包含多个时段，请按教师、星期和节次拆开：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			TeacherResolution teacher = resolveTeacher(rule, scope);
			Integer day = resolveDay(rule);
			Integer period = resolvePeriod(rule);
			String segment = containsAny(rule, "上午", "下午", "晚上")
					? rule.contains("上午") ? "MORNING" : rule.contains("下午") ? "AFTERNOON" : "EVENING"
					: null;
			if (containsAny(rule, "课程", "教室", "连堂", "年级", "班级", "单双周", "每周",
					"避免", "跨校区", "集中", "节连")
					|| rule.replace("上课", "").replace("排课", "").contains("课")
					|| segment != null && period != null
					|| containsAny(rule, "上午", "下午", "晚上")
						&& List.of("上午", "下午", "晚上").stream()
								.filter(rule::contains).count() > 1) {
				unsupported.add("该时间或业务规则还不能转成具体排课条件：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			if (teacher.candidates().isEmpty()) {
				clarifications.add("未能从授权教师数据中识别教师，请提供教师姓名或工号");
			} else if (teacher.candidates().size() > 1) {
				clarifications.add("教师名称存在多个匹配：" + teacher.candidates().stream()
						.map(TeacherAcademicProfile::getTeacherName)
						.distinct()
						.sorted()
						.toList());
			}
			if (day == null) {
				clarifications.add("请明确星期几");
			} else if (day > 5) {
				unsupported.add("当前排课求解器仅支持周一至周五：" + rule);
				unresolvedClauses.add(rule);
				continue;
			}
			if (segment == null && (period == null || period < 1 || period > 20)) {
				clarifications.add("请明确有效节次（1-20）");
			}
			if (teacher.unique() != null && day != null
					&& (segment != null || period != null && period >= 1 && period <= 20)) {
				String normalized = rule.replace(teacher.unique().getTeacherName(), "TEACHER");
				if (teacher.unique().getTeacherNo() != null
						&& !teacher.unique().getTeacherNo().isBlank()) {
					normalized = normalized.replace(teacher.unique().getTeacherNo(), "TEACHER");
				}
				if (!(segment == null ? SUPPORTED_TEACHER_SLOT : SUPPORTED_TEACHER_SEGMENT)
						.matcher(normalized).matches()) {
					unsupported.add("该表述不能安全地转换为教师时段规则：" + rule);
					unresolvedClauses.add(rule);
					continue;
				}
				List<Integer> resolvedPeriods;
				if (segment != null) {
					if (timetable == null) {
						throw new IllegalStateException("AI 作息映射服务不可用");
					}
					var result = timetable.segment(request.semesterCode(), teacher.unique().getId(),
							request.mode(), selected, segment);
					if (result.clarification() != null) {
						clarifications.add(result.clarification() + "：" + rule);
						unresolvedClauses.add(rule);
						continue;
					}
					resolvedPeriods = result.periodNumbers();
					if (resolvedPeriods == null || resolvedPeriods.isEmpty()) {
						throw new IllegalStateException("作息时段映射没有可排课节次");
					}
				} else {
					resolvedPeriods = List.of(period);
				}
				if (resolvedPeriods.stream().anyMatch(resolved ->
						constraints.stream().anyMatch(existing ->
								existing.teacherId().equals(teacher.unique().getId())
										&& existing.dayOfWeek().equals(day)
										&& existing.periodNo().equals(resolved)
										&& !existing.strength().equals(preferred ? "SOFT" : "HARD")))) {
					clarifications.add("同一教师时段同时包含禁排和偏好：" + rule);
					unresolvedClauses.add(rule);
					continue;
				}
				for (Integer resolved : resolvedPeriods) {
					constraints.add(new SchedulingAiConstraint(
							"TEACHER_TIME",
							preferred ? "SOFT" : "HARD",
							teacher.unique().getId(),
							teacher.unique().getTeacherName(),
							day,
							resolved,
							segment,
							rule,
							"RESOLVED"));
				}
			} else {
				unresolvedClauses.add(rule);
			}
		}
		SchedulingAiPlan plan = new SchedulingAiPlan(
				1,
				"SCHEDULE_REQUIREMENTS_V1",
				request.semesterCode(),
				request.mode(),
				selected,
				request.candidateCount(),
				constraints,
				clarifications,
				unsupported,
				unresolvedClauses,
				offeringConstraints,
				weekRules,
				lockedEntries,
				softPriorities);
		return new ParsedRequirement(plan);
	}

	private void parseWeekRule(SchedulingAiRunRequest request, EducationDataScope scope,
			Set<String> selected, String source, List<SchedulingAiWeekRule> parsed,
			List<String> clarifications, List<String> unsupported,
			List<String> unresolved) {
		Matcher matcher = WEEK_RULE_PATTERN.matcher(source);
		if (!matcher.matches()) {
			unsupported.add("教学周规则仅支持明确课程/教学任务的单双周或连续起止周：" + source);
			unresolved.add(source);
			return;
		}
		var term = terms.findByTermCode(request.semesterCode()).orElseThrow();
		int weekCount = term.getWeekCount() == null ? 0 : term.getWeekCount();
		if (!"ACTIVE".equals(term.getStatus()) || weekCount < 1 || weekCount > 52) {
			clarifications.add("学期教学周数无效，无法解析教学周规则：" + source);
			unresolved.add(source);
			return;
		}
		ResolvedOffering resolved = resolveOffering(scope, request.semesterCode(), request.mode(),
				selected, matcher.group(1), source);
		if (resolved.offering() == null) {
			clarifications.add(resolved.clarification());
			unresolved.add(source);
			return;
		}
		String pattern = matcher.group(2) == null ? "ALL" : switch (matcher.group(2)) {
			case "单周", "奇数周" -> "ODD";
			case "双周", "偶数周" -> "EVEN";
			default -> "ALL";
		};
		int start;
		int end;
		try {
			String startValue = matcher.group(3) != null ? matcher.group(3) : matcher.group(5);
			String endValue = matcher.group(4) != null ? matcher.group(4) : matcher.group(6);
			start = startValue == null ? 1 : Integer.parseInt(startValue);
			end = endValue == null ? weekCount : Integer.parseInt(endValue);
		} catch (NumberFormatException exception) {
			clarifications.add("教学周次超出有效范围：" + source);
			unresolved.add(source);
			return;
		}
		if (start < 1 || end > weekCount || end < start) {
			clarifications.add("教学周范围必须是本学期内有效的连续周次：" + source);
			unresolved.add(source);
			return;
		}
		if (!hasMatchingWeek(pattern, start, end)) {
			clarifications.add("单双周规则与指定周次范围没有交集：" + source);
			unresolved.add(source);
			return;
		}
		SchedulingAiWeekRule next = new SchedulingAiWeekRule(
				resolved.offering().getId(), pattern, start, end, source);
		SchedulingAiWeekRule existing = parsed.stream()
				.filter(item -> item.offeringId().equals(next.offeringId()))
				.findFirst().orElse(null);
		if (existing != null) {
			boolean conflict = !existing.weekPattern().equals(next.weekPattern())
					|| existing.startWeek() != next.startWeek()
					|| existing.endWeek() != next.endWeek();
			clarifications.add(conflict
					? "同一教学任务包含相互冲突的教学周规则：" + source
					: "同一教学任务重复指定教学周规则，请保留一条：" + source);
			unresolved.add(source);
			return;
		}
		parsed.add(next);
	}

	private void parseLockedEntry(SchedulingAiRunRequest request, EducationDataScope scope,
			Set<String> selected, String source, List<SchedulingAiLockedEntry> parsed,
			List<String> clarifications, List<String> unsupported,
			List<String> unresolved) {
		if (scheduleEntries == null) {
			throw new IllegalStateException("现有课表查询服务不可用");
		}
		Set<String> targets = targetOfferings(scope, request.semesterCode(), request.mode(), selected)
				.stream().map(CourseOffering::getId)
				.collect(java.util.stream.Collectors.toSet());
		List<ScheduleEntry> currentEntries = scheduleEntries
				.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(request.semesterCode()).stream()
				.filter(entry -> request.semesterCode().equals(entry.getSemesterCode()))
				.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
				.filter(entry -> targets.contains(entry.getOfferingId()))
				.filter(entry -> entry.getDayOfWeek() != null && entry.getPeriodNo() != null
						&& entry.getDayOfWeek() >= 1 && entry.getDayOfWeek() <= 7
						&& entry.getPeriodNo() >= 1 && entry.getPeriodNo() <= 20)
				.toList();
		Matcher idMatcher = LOCK_ENTRY_PATTERN.matcher(source);
		Matcher slotMatcher = LOCK_ENTRY_SLOT_PATTERN.matcher(source);
		Matcher offeringMatcher = LOCK_ENTRY_OFFERING_PATTERN.matcher(source);
		List<ScheduleEntry> matches;
		if (idMatcher.matches()) {
			String entryId = idMatcher.group(1);
			matches = currentEntries.stream().filter(entry -> entryId.equals(entry.getId())).toList();
		} else if (slotMatcher.matches()) {
			Integer day = resolveDay(slotMatcher.group(2));
			Integer period = chineseNumber(slotMatcher.group(3));
			ResolvedOffering offering = resolveOffering(scope, request.semesterCode(),
					request.mode(), selected, slotMatcher.group(1), source);
			if (offering.offering() == null || day == null || period == null) {
				clarifications.add(offering.clarification() != null
						? offering.clarification()
						: "请明确课表项的有效星期和节次：" + source);
				unresolved.add(source);
				return;
			}
			matches = currentEntries.stream()
					.filter(entry -> offering.offering().getId().equals(entry.getOfferingId()))
					.filter(entry -> day.equals(entry.getDayOfWeek())
							&& period.equals(entry.getPeriodNo()))
					.toList();
		} else {
			Matcher matcher = LOCK_ENTRY_OFFERING_PATTERN.matcher(source);
			String offeringPhrase;
			if (matcher.matches()) {
				offeringPhrase = matcher.group(1);
			} else {
				matcher = LOCK_ENTRY_ALIAS_PATTERN.matcher(source);
				if (!matcher.matches()) {
				unsupported.add("临时保留规则须指定条目 ID，或唯一课程及星期节次：" + source);
				unresolved.add(source);
				return;
				}
				offeringPhrase = matcher.group(1);
			}
			ResolvedOffering offering = resolveOffering(scope, request.semesterCode(),
					request.mode(), selected, offeringPhrase, source);
			if (offering.offering() == null) {
				clarifications.add(offering.clarification());
				unresolved.add(source);
				return;
			}
			matches = currentEntries.stream()
					.filter(entry -> offering.offering().getId().equals(entry.getOfferingId()))
					.toList();
		}
		if (matches.isEmpty()) {
			clarifications.add("当前授权排课范围内未找到匹配的现有课表项：" + source);
			unresolved.add(source);
			return;
		}
		if (matches.size() != 1) {
			clarifications.add("临时保留规则匹配多个现有课表项，请提供唯一条目 ID 或星期节次：" + source);
			unresolved.add(source);
			return;
		}
		ScheduleEntry entry = matches.getFirst();
		CourseOffering offering = offerings.findById(entry.getOfferingId()).orElse(null);
		if (offering == null || !targets.contains(entry.getOfferingId())
				|| !dataScopes.canAccessOffering(scope, offering)) {
			clarifications.add("该课表条目不属于当前授权排课范围：" + entry.getId());
			unresolved.add(source);
			return;
		}
		if (parsed.stream().anyMatch(existing -> existing.entryId().equals(entry.getId()))) {
			clarifications.add("同一课表项被重复指定临时保留：" + source);
			unresolved.add(source);
			return;
		}
		parsed.add(new SchedulingAiLockedEntry(entry.getId(), entry.getOfferingId(),
				entry.getDayOfWeek(), entry.getPeriodNo(), source));
	}

	private void parseSoftPriority(String source, EducationDataScope scope,
			List<SchedulingAiSoftPriority> parsed, List<String> clarifications,
			List<String> unsupported, List<String> unresolved) {
		Matcher matcher = SOFT_PRIORITY_PATTERN.matcher(source);
		if (!matcher.matches()) {
			unsupported.add("教师软偏好仅支持减少跨校区/空档或同日集中：" + source);
			unresolved.add(source);
			return;
		}
		TeacherResolution resolved = resolveTeacher(matcher.group(1), scope);
		if (resolved.candidates().isEmpty()) {
			clarifications.add("未能从授权教师数据中识别教师：" + source);
			unresolved.add(source);
			return;
		}
		if (resolved.candidates().size() != 1) {
			clarifications.add("教师名称存在多个匹配，无法应用软偏好：" + source);
			unresolved.add(source);
			return;
		}
		String kind = switch (matcher.group(2)) {
			case "减少跨校区", "减少校区切换", "避免跨校区", "跨校区切换少" -> "CAMPUS_SWITCH";
			case "减少空档", "减少教师空档", "避免空档" -> "TEACHER_GAP";
			default -> "SAME_DAY";
		};
		TeacherAcademicProfile teacher = resolved.unique();
		if (parsed.stream().anyMatch(existing ->
				existing.teacherId().equals(teacher.getId()) && existing.kind().equals(kind))) {
			clarifications.add("同一教师软偏好重复指定：" + source);
			unresolved.add(source);
			return;
		}
		parsed.add(new SchedulingAiSoftPriority(kind, teacher.getId(),
				teacher.getTeacherName(), source));
	}

	private ResolvedOffering resolveOffering(EducationDataScope scope, String semester,
			String mode, Set<String> selected, String phrase, String source) {
		String entity = phrase.strip();
		TeacherResolution teacher = resolveTeacher(entity, scope);
		if (teacher.candidates().size() > 1) {
			return new ResolvedOffering(null, "周次规则中的教师名称存在多个匹配：" + source);
		}
		if (teacher.unique() != null) {
			entity = entity.replace(teacher.unique().getTeacherName(), "")
					.replace("的", "").strip();
			if (teacher.unique().getTeacherNo() != null) {
				entity = entity.replace(teacher.unique().getTeacherNo(), "").strip();
			}
		}
		List<CourseOffering> candidates = targetOfferings(scope, semester, mode, selected).stream()
				.filter(offering -> teacher.unique() == null
						|| teacher.unique().getId().equals(offering.getTeacherId()))
				.toList();
		if (entity.isBlank() && teacher.unique() != null) {
			if (candidates.size() == 1) {
				return new ResolvedOffering(candidates.getFirst(), null);
			}
			return new ResolvedOffering(null, "该教师对应多个授权教学任务，请指定唯一任务：" + source);
		}
		if (entity.isBlank()) {
			return new ResolvedOffering(null, "请明确唯一课程或教学任务：" + source);
		}
		String offeringReference = entity;
		List<CourseOffering> matches = candidates.stream()
				.filter(offering -> offeringAliases(offering).stream()
						.anyMatch(alias -> normalizeEntity(alias).equals(normalizeEntity(offeringReference))))
				.toList();
		if (matches.isEmpty()) {
			return new ResolvedOffering(null, "未能从授权排课范围识别周次规则对应的课程/教学任务：" + source);
		}
		if (matches.size() != 1) {
			return new ResolvedOffering(null, "课程或教学任务存在多个匹配，请使用唯一教学任务编码：" + source);
		}
		return new ResolvedOffering(matches.getFirst(), null);
	}

	private List<CourseOffering> targetOfferings(EducationDataScope scope, String semester,
			String mode, Set<String> selected) {
		return dataScopes.visibleOfferings(scope,
						offerings.findBySemesterCodeOrderByOfferingCode(semester)).stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.filter(offering -> "GLOBAL".equals(mode) || selected.contains(offering.getId()))
				.toList();
	}

	private List<String> offeringAliases(CourseOffering offering) {
		return java.util.stream.Stream.of(offering.getOfferingCode(), offering.getCourseCode(),
						offering.getCourseName(), offering.getTeachingClassName())
				.filter(value -> value != null && !value.isBlank()).toList();
	}

	private String normalizeEntity(String value) {
		return value.replaceAll("\\s+", "").strip();
	}

	private boolean hasMatchingWeek(String pattern, int start, int end) {
		return switch (pattern) {
			case "ODD" -> (start % 2 != 0) || start < end;
			case "EVEN" -> (start % 2 == 0) || start < end;
			default -> true;
		};
	}

	private boolean isLockEntryForm(String source) {
		return LOCK_ENTRY_PATTERN.matcher(source).matches()
				|| LOCK_ENTRY_SLOT_PATTERN.matcher(source).matches()
				|| LOCK_ENTRY_OFFERING_PATTERN.matcher(source).matches()
				|| LOCK_ENTRY_ALIAS_PATTERN.matcher(source).matches();
	}

	private boolean isGenerationInstruction(String rule) {
		return rule.matches("(请|帮我|帮忙)?(生成|进行)?(全局|局部)?(走班)?排课(方案)?")
				|| rule.matches("(请|帮我)?生成[一二三四五\\d]*个?(候选)?方案");
	}

	private void validateMentionedSemester(
			SchedulingAiRunRequest request,
			String text,
			List<String> clarifications) {
		List<String> mentioned = terms.findAllByOrderByStartDateDesc().stream()
				.filter(term -> text.contains(term.getTermCode())
						|| text.contains(term.getTermName()))
				.map(com.chronos.education.scheduling.model.AcademicTerm::getTermCode)
				.distinct()
				.toList();
		if (mentioned.size() == 1 && !request.semesterCode().equals(mentioned.getFirst())) {
			clarifications.add("自然语言中的学期与请求 semesterCode 不一致，请确认 "
					+ request.semesterCode());
		} else if (mentioned.size() > 1) {
			clarifications.add("自然语言中出现多个学期，请只保留一个学期");
		}
	}

	private void validateMentionedMode(
			SchedulingAiRunRequest request,
			String text,
			List<String> clarifications) {
		String mentioned = text.contains("局部") || text.contains("指定教学任务")
				? "LOCAL"
				: text.contains("全局") || text.contains("全部教学任务")
						? "GLOBAL" : null;
		if (mentioned != null && !mentioned.equals(request.mode())) {
			clarifications.add("自然语言中的排课模式与请求 mode 不一致，请确认 " + request.mode());
		}
	}

	public String requestHash(SchedulingAiRunRequest request) {
		String value = String.join(
				"|",
				request.clientRequestId(),
				request.semesterCode(),
				request.mode(),
				request.selectedOfferingIds().stream().sorted().toList().toString(),
				Integer.toString(request.candidateCount()),
				request.requestText());
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("请求摘要算法不可用", exception);
		}
	}

	private Set<String> validateSelectedOfferings(
			SchedulingAiRunRequest request,
			EducationDataScope scope) {
		if (request.selectedOfferingIds().isEmpty()) {
			return Set.of();
		}
		List<CourseOffering> visible = dataScopes.visibleOfferings(
				scope,
				offerings.findBySemesterCodeOrderByOfferingCode(request.semesterCode()));
		Set<String> visibleIds = visible.stream().map(CourseOffering::getId)
				.collect(java.util.stream.Collectors.toSet());
		if (!visibleIds.containsAll(request.selectedOfferingIds())) {
			throw new IllegalArgumentException("selectedOfferingIds 包含不存在或无权访问的教学任务");
		}
		return Set.copyOf(new LinkedHashSet<>(request.selectedOfferingIds()));
	}

	private TeacherResolution resolveTeacher(String text, EducationDataScope scope) {
		List<TeacherAcademicProfile> candidates = teachers.findAllByOrderByTeacherNo().stream()
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
				.filter(item -> dataScopes.canAccessTeacher(scope, item.getId()))
				.filter(item -> text.contains(item.getTeacherName())
						|| text.contains(item.getTeacherNo()))
				.sorted(Comparator.comparing(TeacherAcademicProfile::getTeacherNo))
				.toList();
		return new TeacherResolution(candidates);
	}

	private Integer resolveDay(String text) {
		return DAYS.entrySet().stream()
				.filter(entry -> text.contains(entry.getKey()))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(null);
	}

	private Integer resolvePeriod(String text) {
		Matcher matcher = PERIOD_PATTERN.matcher(text);
		return matcher.find() ? chineseNumber(matcher.group(1)) : null;
	}

	private Integer chineseNumber(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			List<String> digits = List.of("一", "二", "三", "四", "五", "六", "七", "八", "九");
			if ("十".equals(value)) {
				return 10;
			}
			if (value.startsWith("十") && value.length() == 2) {
				int digit = digits.indexOf(value.substring(1)) + 1;
				return digit > 0 ? 10 + digit : null;
			}
			if ("二十".equals(value)) {
				return 20;
			}
			int result = digits.indexOf(value) + 1;
			return result < 1 ? null : result;
		}
	}

	private boolean containsAny(String text, String... values) {
		for (String value : values) {
			if (text.contains(value)) {
				return true;
			}
		}
		return false;
	}

	public record ParsedRequirement(SchedulingAiPlan plan) {
	}

	private record TeacherResolution(List<TeacherAcademicProfile> candidates) {
		private TeacherAcademicProfile unique() {
			return candidates.size() == 1 ? candidates.get(0) : null;
		}
	}

	private record ResolvedOffering(CourseOffering offering, String clarification) {
	}
}
