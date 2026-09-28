package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.SchedulingAiConstraint;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
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
	private static final Pattern SUPPORTED_TEACHER_SLOT = Pattern.compile(
			"(?:请|帮我|给)?\\s*TEACHER\\s*(?:在|于)?"
					+ "(?:星期[一二三四五六日天]|周[一二三四五六日天])\\s*"
					+ "第?[一二三四五六七八九十\\d]+节\\s*"
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

	@Autowired
	public SchedulingAiRequirementParser(
			AcademicTermRepository terms,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			EducationDataScopeService dataScopes,
			SchedulingAiModelClassifier modelClassifier) {
		this.terms = terms;
		this.offerings = offerings;
		this.teachers = teachers;
		this.dataScopes = dataScopes;
		this.modelClassifier = modelClassifier;
	}

	SchedulingAiRequirementParser(AcademicTermRepository terms,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			EducationDataScopeService dataScopes) {
		this(terms, offerings, teachers, dataScopes, null);
	}

	public ParsedRequirement parse(SchedulingAiRunRequest request, String username) {
		terms.findByTermCode(request.semesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		EducationDataScope scope = dataScopes.resolve(username);
		Set<String> selected = validateSelectedOfferings(request, scope);
		List<String> clarifications = new java.util.ArrayList<>();
		List<SchedulingAiConstraint> constraints = new java.util.ArrayList<>();
		List<String> unsupported = new java.util.ArrayList<>();
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
				continue;
			}
			if ("GENERATION".equals(clause.classification()) != isGenerationInstruction(rule)
					&& !"DETERMINISTIC".equals(clause.classification())) {
				unsupported.add("模型分类与服务端规则不一致：" + rule);
				continue;
			}
			if (rule.isEmpty() || isGenerationInstruction(rule)) {
				continue;
			}
			boolean forbidden = containsAny(rule, "不能", "不可", "禁止", "禁排", "不排");
			boolean preferred = containsAny(rule, "尽量", "优先", "希望", "最好");
			if (!forbidden && !preferred) {
				unsupported.add("暂不支持该需求：" + rule);
				continue;
			}
			if (forbidden && preferred) {
				clarifications.add("同一条规则同时包含禁排和偏好，请拆开描述：" + rule);
				continue;
			}
			if (DAYS.entrySet().stream().filter(entry -> rule.contains(entry.getKey()))
					.map(Map.Entry::getValue).distinct().count() > 1
					|| PERIOD_PATTERN.matcher(rule).results().count() > 1) {
				unsupported.add("一条规则包含多个时段，请按教师、星期和节次拆开：" + rule);
				continue;
			}
			TeacherResolution teacher = resolveTeacher(rule, scope);
			Integer day = resolveDay(rule);
			Integer period = resolvePeriod(rule);
			if (containsAny(rule, "课程", "教室", "连堂", "年级", "班级", "单双周", "每周",
					"上午", "下午", "避免", "跨校区", "集中", "节连")) {
				unsupported.add("该时间或业务规则还不能转成具体排课条件：" + rule);
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
			}
			if (period == null || period < 1 || period > 20) {
				clarifications.add("请明确有效节次（1-20）");
			}
			if (teacher.unique() != null && day != null && period != null
					&& period >= 1 && period <= 20) {
				String normalized = rule.replace(teacher.unique().getTeacherName(), "TEACHER");
				if (teacher.unique().getTeacherNo() != null
						&& !teacher.unique().getTeacherNo().isBlank()) {
					normalized = normalized.replace(teacher.unique().getTeacherNo(), "TEACHER");
				}
				if (!SUPPORTED_TEACHER_SLOT.matcher(normalized).matches()) {
					unsupported.add("该表述不能安全地转换为教师单节规则：" + rule);
					continue;
				}
				if (constraints.stream().anyMatch(existing ->
						existing.teacherId().equals(teacher.unique().getId())
								&& existing.dayOfWeek().equals(day)
								&& existing.periodNo().equals(period)
								&& !existing.strength().equals(preferred ? "SOFT" : "HARD"))) {
					clarifications.add("同一教师时段同时包含禁排和偏好：" + rule);
					continue;
				}
				constraints.add(new SchedulingAiConstraint(
						"TEACHER_TIME",
						preferred ? "SOFT" : "HARD",
						teacher.unique().getId(),
						teacher.unique().getTeacherName(),
						day,
						period,
						null,
						rule,
						"RESOLVED"));
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
				unsupported);
		return new ParsedRequirement(plan, toCommand(plan));
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
			List<String> values = List.of("一", "二", "三", "四", "五", "六", "七", "八", "九", "十");
			int result = values.indexOf(value) + 1;
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

	private AutoScheduleCommand toCommand(SchedulingAiPlan plan) {
		return new AutoScheduleCommand(
				plan.semesterCode(),
				"AI-" + plan.semesterCode(),
				"LOCAL".equals(plan.mode()) ? "LOCAL" : "FULL",
				plan.selectedOfferingIds(),
				plan.candidateCount(),
				5,
				8,
				1,
				20);
	}

	public record ParsedRequirement(
			SchedulingAiPlan plan,
			AutoScheduleCommand command) {
	}

	private record TeacherResolution(List<TeacherAcademicProfile> candidates) {
		private TeacherAcademicProfile unique() {
			return candidates.size() == 1 ? candidates.get(0) : null;
		}
	}
}
