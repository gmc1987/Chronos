package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Rebuilds solver inputs only from the server-confirmed plan, never a tool caller's payload. */
@Service
public class SchedulingAgentPlanValidator {
	private final EducationDataScopeService scopes;
	private final ObjectMapper json;
	private final SchedulingAgentTimetableService timetable;
	private final CourseOfferingRepository offerings;
	private final ScheduleEntryRepository entries;
	private final TeacherAcademicProfileRepository teachers;

	@Autowired
	public SchedulingAgentPlanValidator(EducationDataScopeService scopes, ObjectMapper json,
			SchedulingAgentTimetableService timetable, CourseOfferingRepository offerings,
			ScheduleEntryRepository entries, TeacherAcademicProfileRepository teachers) {
		this.scopes = scopes;
		this.json = json;
		this.timetable = timetable;
		this.offerings = offerings;
		this.entries = entries;
		this.teachers = teachers;
	}

	public SchedulingAgentPlanValidator(EducationDataScopeService scopes, ObjectMapper json,
			SchedulingAgentTimetableService timetable) {
		this(scopes, json, timetable, null, null, null);
	}

	public Parameters fromConfirmedRun(AgentRun run, String actor) {
		if (run.getConfirmedPlanJson() == null || run.getConfirmedPlanJson().isBlank()) {
			throw new IllegalStateException("AI Run 尚未确认");
		}
		try {
			SchedulingAiPlan plan = json.readValue(run.getConfirmedPlanJson(), SchedulingAiPlan.class);
			if (!run.getSemesterCode().equals(plan.semesterCode())) {
				throw new IllegalStateException("AI Run 的学期与确认计划不一致");
			}
			return parameters(plan, actor);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI Run 确认计划损坏", exception);
		}
	}

	public SchedulingAiPlan fromDraftRun(AgentRun run) {
		if (run.getParsedPlanJson() == null || run.getParsedPlanJson().isBlank()) {
			throw new IllegalStateException("AI Run 缺少待确认计划");
		}
		try {
			SchedulingAiPlan plan = json.readValue(run.getParsedPlanJson(), SchedulingAiPlan.class);
			if (!run.getSemesterCode().equals(plan.semesterCode())) {
				throw new IllegalStateException("AI Run 的学期与待确认计划不一致");
			}
			return plan;
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI Run 待确认计划损坏", exception);
		}
	}

	public Parameters parameters(SchedulingAiPlan plan, String actor) {
		if (!plan.readyForConfirmation() || plan.candidateCount() < 1 || plan.candidateCount() > 5
				|| plan.mode() == null || !Set.of("GLOBAL", "LOCAL").contains(plan.mode())
				|| "LOCAL".equals(plan.mode()) && plan.selectedOfferingIds().isEmpty()) {
			throw new IllegalStateException("结构化需求不能执行排课");
		}
		var scope = scopes.resolve(actor);
		scopes.assertFullAccess(scope);
		var dimensions = timetable.dimensions(plan.semesterCode(), plan.mode(),
				plan.selectedOfferingIds());
		Set<String> targetIds = timetable.targetOfferingIds(plan.semesterCode(), plan.mode(),
				plan.selectedOfferingIds());
		Map<String, CourseOffering> sourceOfferings = sourceOfferings(plan, targetIds, scope);
		if (plan.offeringConstraints().stream()
				.map(com.chronos.education.scheduling.model.SchedulingAiOfferingConstraint::offeringId)
				.distinct().count() != plan.offeringConstraints().size()) {
			throw new IllegalStateException("课程连堂规则重复");
		}
		List<ScheduleRunConstraints.OfferingDuration> durationRules =
				plan.offeringConstraints().stream()
						.map(item -> {
							if (item.offeringId() == null || !targetIds.contains(item.offeringId())
									|| item.periods() != 2
									|| item.sourceText() == null || item.sourceText().isBlank()) {
								throw new IllegalStateException("课程连堂规则不在当前排课范围内");
							}
							if (offerings != null && !mentionsOffering(item.sourceText(),
									requireSourceOffering(sourceOfferings, item.offeringId(),
											item.sourceText()))) {
								throw new IllegalStateException("课程连堂规则与当前授权课程数据不一致");
							}
							return new ScheduleRunConstraints.OfferingDuration(
									item.offeringId(), item.periods());
						}).toList();
		List<ScheduleRunConstraints.TeacherSlot> rules = plan.constraints().stream()
				.map(item -> {
					if (!"TEACHER_TIME".equals(item.kind())
							|| !"RESOLVED".equals(item.resolution())
							|| item.strength() == null
							|| !Set.of("HARD", "SOFT").contains(item.strength())
							|| item.teacherId() == null
							|| item.teacherName() == null
							|| item.sourceText() == null || item.sourceText().isBlank()
							|| item.dayOfWeek() == null || item.periodNo() == null
							|| item.dayOfWeek() < 1 || item.periodNo() < 1
							|| item.dayOfWeek() > dimensions.weekdays()
							|| item.periodNo() > dimensions.periodsPerDay()) {
						throw new IllegalStateException("结构化规则未被求解器支持");
					}
					scopes.assertTeacherAccess(scope, item.teacherId());
					if (teachers != null) {
						TeacherAcademicProfile teacher = teachers.findById(item.teacherId()).orElse(null);
						if (teacher == null || !Boolean.TRUE.equals(teacher.getEnabled())
								|| !sameTeacherSource(item.sourceText(), teacher)
								|| !java.util.Objects.equals(teacher.getTeacherName(), item.teacherName())) {
							throw new IllegalStateException("确认计划中的教师时段与当前授权数据不一致");
						}
					}
					return new ScheduleRunConstraints.TeacherSlot(
							item.teacherId(), item.dayOfWeek(), item.periodNo(),
							"HARD".equals(item.strength()) ? "FORBIDDEN" : "PREFERRED");
				})
				.toList();
		if (plan.weekRules().stream().map(
				com.chronos.education.scheduling.model.SchedulingAiWeekRule::offeringId)
				.distinct().count() != plan.weekRules().size()) {
			throw new IllegalStateException("教学周规则重复");
		}
		List<ScheduleRunConstraints.WeekRule> weekRules = plan.weekRules().stream()
				.map(item -> {
					CourseOffering offering = requireSourceOffering(
							sourceOfferings, item.offeringId(), item.sourceText());
					if (item.startWeek() < 1 || item.endWeek() > dimensions.endWeek()
							|| item.endWeek() < item.startWeek()
							|| item.weekPattern() == null
							|| !Set.of("ALL", "ODD", "EVEN").contains(item.weekPattern())
							|| !hasMatchingWeek(item.weekPattern(), item.startWeek(), item.endWeek())
							|| !identifiesOffering(item.sourceText(), offering)) {
						throw new IllegalStateException("确认计划中的教学周规则与当前学期数据不一致");
					}
					return new ScheduleRunConstraints.WeekRule(
							item.offeringId(), item.weekPattern(), item.startWeek(), item.endWeek());
				}).toList();
		if (plan.lockedEntries().stream().map(
				com.chronos.education.scheduling.model.SchedulingAiLockedEntry::entryId)
				.distinct().count() != plan.lockedEntries().size()) {
			throw new IllegalStateException("临时保留课表项重复");
		}
		List<ScheduleRunConstraints.LockedEntry> lockedEntries = plan.lockedEntries().stream()
				.map(item -> {
					requireSourceOffering(sourceOfferings, item.offeringId(), item.sourceText());
					if (entries == null || item.entryId() == null || item.entryId().isBlank()
							|| item.sourceText() == null
							|| !item.sourceText().contains(item.entryId())) {
						throw new IllegalStateException("确认计划中的课表项来源无效");
					}
					ScheduleEntry entry = entries.findById(item.entryId()).orElse(null);
					if (entry == null || !plan.semesterCode().equals(entry.getSemesterCode())
							|| !item.offeringId().equals(entry.getOfferingId())
							|| item.dayOfWeek() < 1 || item.dayOfWeek() > 7
							|| item.periodNo() < 1 || item.periodNo() > dimensions.periodsPerDay()
							|| entry.getDayOfWeek() == null
							|| entry.getDayOfWeek() != item.dayOfWeek()
							|| entry.getPeriodNo() == null
							|| entry.getPeriodNo() != item.periodNo()
							|| entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(
									plan.semesterCode()).stream()
									.filter(current -> plan.semesterCode().equals(current.getSemesterCode()))
									.filter(current -> item.entryId().equals(current.getId()))
									.count() != 1
							|| !identifiesEntry(item.sourceText(), item.entryId(),
									item.dayOfWeek(), item.periodNo(), sourceOfferings.get(item.offeringId()))
							|| "CANCELLED".equals(entry.getStatus())) {
						throw new IllegalStateException("确认计划中的临时保留项已不存在或已变更");
					}
					return new ScheduleRunConstraints.LockedEntry(item.entryId(), item.offeringId(),
							item.dayOfWeek(), item.periodNo());
				}).toList();
		if (plan.softPriorities().stream().map(item -> item.teacherId() + "|" + item.kind())
				.distinct().count() != plan.softPriorities().size()) {
			throw new IllegalStateException("教师软偏好重复");
		}
		List<ScheduleRunConstraints.SoftPriority> softPriorities = plan.softPriorities().stream()
				.map(item -> {
					if (teachers == null || item.teacherId() == null || item.kind() == null
							|| item.sourceText() == null || item.sourceText().isBlank()
							|| !Set.of("CAMPUS_SWITCH", "TEACHER_GAP", "SAME_DAY")
									.contains(item.kind())) {
						throw new IllegalStateException("确认计划中的教师软偏好无效");
					}
					scopes.assertTeacherAccess(scope, item.teacherId());
					TeacherAcademicProfile teacher = teachers.findById(item.teacherId()).orElse(null);
					if (teacher == null || !Boolean.TRUE.equals(teacher.getEnabled())
							|| !sameTeacherSource(item.sourceText(), teacher)
							|| sourceOfferings.values().stream().noneMatch(offering ->
									item.teacherId().equals(offering.getTeacherId()))) {
						throw new IllegalStateException("确认计划中的教师软偏好与当前授权数据不一致");
					}
					return new ScheduleRunConstraints.SoftPriority(item.kind(), item.teacherId());
				}).toList();
		AutoScheduleCommand command = new AutoScheduleCommand(
				plan.semesterCode(), "AI-" + plan.semesterCode(),
				"LOCAL".equals(plan.mode()) ? "LOCAL" : "FULL",
				plan.selectedOfferingIds(), plan.candidateCount(),
				dimensions.weekdays(), dimensions.periodsPerDay(), 1, dimensions.endWeek());
		return new Parameters(command, new ScheduleRunConstraints(
				rules, durationRules, weekRules, lockedEntries, softPriorities));
	}

	private Map<String, CourseOffering> sourceOfferings(SchedulingAiPlan plan, Set<String> targetIds,
			com.chronos.education.scheduling.model.EducationDataScope scope) {
		if (offerings == null) {
			if (!plan.weekRules().isEmpty() || !plan.lockedEntries().isEmpty()
					|| !plan.softPriorities().isEmpty()) {
				throw new IllegalStateException("确认计划来源数据不可用");
			}
			return Map.of();
		}
		return scopes.visibleOfferings(scope,
						offerings.findBySemesterCodeOrderByOfferingCode(plan.semesterCode())).stream()
				.filter(item -> "ACTIVE".equals(item.getStatus()))
				.filter(item -> targetIds.contains(item.getId()))
				.collect(Collectors.toMap(CourseOffering::getId, item -> item));
	}

	private CourseOffering requireSourceOffering(Map<String, CourseOffering> sourceOfferings,
			String offeringId, String sourceText) {
		CourseOffering offering = offeringId == null ? null : sourceOfferings.get(offeringId);
		if (offering == null || sourceText == null || sourceText.isBlank()) {
			throw new IllegalStateException("确认计划规则不属于当前授权教学任务");
		}
		return offering;
	}

	private boolean mentionsOffering(String source, CourseOffering offering) {
		return java.util.stream.Stream.of(offering.getOfferingCode(), offering.getCourseCode(),
						offering.getCourseName(), offering.getTeachingClassName())
				.filter(value -> value != null && !value.isBlank())
				.anyMatch(value -> source.replaceAll("\\s+", "")
						.contains(value.replaceAll("\\s+", "")));
	}

	private boolean identifiesOffering(String source, CourseOffering offering) {
		if (mentionsOffering(source, offering)) {
			return true;
		}
		if (teachers == null || offering.getTeacherId() == null) {
			return false;
		}
		TeacherAcademicProfile teacher = teachers.findById(offering.getTeacherId()).orElse(null);
		return teacher != null && Boolean.TRUE.equals(teacher.getEnabled())
				&& sameTeacherSource(source, teacher);
	}

	private boolean identifiesEntry(String source, String entryId, Integer day, Integer period,
			CourseOffering offering) {
		if (java.util.regex.Pattern.compile("(?<![A-Za-z0-9_-])"
				+ java.util.regex.Pattern.quote(entryId) + "(?![A-Za-z0-9_-])")
				.matcher(source).find()) {
			return true;
		}
		return offering != null && mentionsOffering(source, offering)
				&& mentionsDayAndPeriod(source, day, period);
	}

	private boolean mentionsDayAndPeriod(String source, int day, int period) {
		List<String> dayNames = switch (day) {
			case 1 -> List.of("周一", "星期一");
			case 2 -> List.of("周二", "星期二");
			case 3 -> List.of("周三", "星期三");
			case 4 -> List.of("周四", "星期四");
			case 5 -> List.of("周五", "星期五");
			case 6 -> List.of("周六", "星期六");
			case 7 -> List.of("周日", "周天", "星期日", "星期天");
			default -> List.of();
		};
		boolean dayMatched = dayNames.stream().anyMatch(source::contains);
		boolean periodMatched = java.util.regex.Pattern.compile(
				"第?" + period + "节").matcher(source).find()
				|| source.contains("第" + chinesePeriod(period) + "节");
		return dayMatched && periodMatched;
	}

	private String chinesePeriod(int period) {
		List<String> values = List.of("", "一", "二", "三", "四", "五", "六", "七", "八", "九");
		if (period == 10) {
			return "十";
		}
		if (period > 10 && period < 20) {
			return "十" + values.get(period - 10);
		}
		return period == 20 ? "二十" : period < values.size() ? values.get(period) : "";
	}

	private boolean hasMatchingWeek(String pattern, int start, int end) {
		return switch (pattern) {
			case "ODD" -> start % 2 != 0 || start < end;
			case "EVEN" -> start % 2 == 0 || start < end;
			default -> true;
		};
	}

	private boolean sameTeacherSource(String source, TeacherAcademicProfile teacher) {
		return teacher.getTeacherName() != null && source.contains(teacher.getTeacherName())
				|| teacher.getTeacherNo() != null && !teacher.getTeacherNo().isBlank()
						&& source.contains(teacher.getTeacherNo());
	}

	public record Parameters(AutoScheduleCommand command, ScheduleRunConstraints constraints) {
	}
}
