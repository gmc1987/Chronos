package com.chronos.education.scheduling.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** 按已发布的日期课次汇总应授，实际分钟取自最新的授课确认版本。 */
@Service
@RequiredArgsConstructor
public class TeachingHoursService {
	private final AcademicTermRepository terms;
	private final TeachingSuspensionService suspensions;
	private final EducationDataScopeService scopes;
	private final ClassSchedulingService scheduling;
	private final SchedulePlanVersionService versions;
	private final ScheduleOccurrenceService calendar;
	private final BellScheduleRepository bells;
	private final BellPeriodRepository periods;
	private final TeacherAcademicProfileRepository teachers;
	private final TeachingHoursEvidenceStore evidence;
	private final EducationResourceTransactionLock lock;
	private final IAuditLogService audit;
	private final ObjectMapper mapper;

	public record Query(String organizationId, String semesterCode, LocalDate startDate, LocalDate endDate,
			String teacherId, String courseCode, String offeringId) {
	}

	public record Lesson(String key, String sourceEntryId, String campusId, LocalDate date, LocalDate sourceDate,
			String offeringId, String teachingClassName, String courseCode, String courseName, String teacherId,
			String teacherName, String originalTeacherName, Integer periodNo, int plannedPeriods, Integer plannedMinutes,
			LocalTime endTime, String scheduleStatus, String adjustmentId, String adjustmentReason, String token) {
	}

	public record Row(Lesson lesson, Integer actualMinutes, int revision, String evidenceStatus, boolean historical) {
	}

	public record Confirmation(String organizationId, String semesterCode, LocalDate date, String key,
			String token, Integer expectedRevision, Integer actualMinutes, Boolean voidConfirmation, String reason) {
	}

	public record Revision(int revision, Integer actualMinutes, String reason, String recordedBy,
			java.time.LocalDateTime recordedAt) {
	}

	@Transactional(readOnly = true)
	public List<Row> list(Query query, String actor) {
		Context ctx = context(query.organizationId(), query.semesterCode(), actor);
		validateRange(ctx.term(), query.startDate(), query.endDate());
		Map<String, TeachingHoursEvidenceStore.Evidence> recorded = evidence.latest(
				query.organizationId(), query.semesterCode(), query.startDate(), query.endDate()).stream()
				.filter(item -> ctx.campuses().contains(item.campusId()))
				.collect(Collectors.toMap(TeachingHoursEvidenceStore.Evidence::occurrenceKey, Function.identity()));

		List<Row> result = new ArrayList<>();
		for (Lesson lesson : lessons(ctx, query.startDate(), query.endDate())) {
			var current = recorded.get(lesson.key());
			if (current != null && current.actualMinutes() != null
					&& !Objects.equals(read(current.snapshot()).teacherId(), lesson.teacherId())) {
				result.add(historical(current));
				result.add(row(lesson, current, null, "CHANGE_REQUIRED", false));
			} else {
				result.add(row(lesson, current, current == null ? null : current.actualMinutes(),
						status(lesson, current), false));
			}
			recorded.remove(lesson.key());
		}
		recorded.values().forEach(item -> result.add(historical(item)));
		return result.stream().filter(item -> matches(query.teacherId(), item.lesson().teacherId()))
				.filter(item -> matches(query.courseCode(), item.lesson().courseCode()))
				.filter(item -> matches(query.offeringId(), item.lesson().offeringId()))
				.sorted(Comparator.comparing((Row item) -> item.lesson().date())
						.thenComparing(item -> item.lesson().periodNo()).thenComparing(item -> item.lesson().key()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<Revision> history(String organization, String term, String key, String actor) {
		Context ctx = context(organization, term, actor);
		return evidence.history(organization, term, key).stream()
				.filter(item -> ctx.campuses().contains(item.campusId()))
				.map(item -> new Revision(item.revision(), item.actualMinutes(), item.reason(), item.recordedBy(),
						item.recordedAt())).toList();
	}

	@Transactional
	public int confirm(Confirmation command, String actor) {
		if (command.key() == null || command.key().isBlank()) {
			throw new IllegalArgumentException("缺少课次标识");
		}
		if (command.expectedRevision() == null || command.expectedRevision() < 0) {
			throw new IllegalArgumentException("缺少有效的确认版本");
		}
		Context ctx = context(command.organizationId(), command.semesterCode(), actor);
		validateRange(ctx.term(), command.date(), command.date());
		String reason = command.reason() == null ? "" : command.reason().trim();
		if (reason.isEmpty() || reason.length() > 500) {
			throw new IllegalArgumentException("请输入 1 至 500 字的确认或更正依据");
		}
		boolean voidConfirmation = Boolean.TRUE.equals(command.voidConfirmation());
		Integer minutes = voidConfirmation ? null : command.actualMinutes();
		if (!voidConfirmation && (minutes == null || minutes < 0 || minutes > 1440)) {
			throw new IllegalArgumentException("实际授课分钟须在 0 到 1440 之间");
		}
		lock.lockSemester(command.semesterCode());
		var previous = evidence.version(command.semesterCode(), command.key()).orElse(null);
		int revision = previous == null ? 0 : previous.revision();
		if (revision != command.expectedRevision()) {
			throw new IllegalStateException("确认记录已变化，请刷新后重试");
		}
		Lesson lesson;
		if (previous != null && previous.actualMinutes() != null) {
			// 更正针对已经入账的凭证，保留当时的授课人和课次。
			lesson = read(previous.snapshot());
			if (!ctx.campuses().contains(previous.campusId()) || !command.date().equals(lesson.date())) {
				throw new AccessDeniedException("无权更正该课次");
			}
		} else {
			if (voidConfirmation) throw new IllegalStateException("当前课次没有可作废的确认");
			lesson = lessons(ctx, command.date(), command.date()).stream()
					.filter(item -> item.key().equals(command.key())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("当前范围没有该课次"));
			if (lesson.plannedPeriods() == 0 || lesson.teacherId() == null
					|| !Objects.equals(lesson.token(), command.token())) {
				throw new IllegalStateException("课次已变化或不可确认，请刷新后核对课表");
			}
			if (lesson.date().isAfter(LocalDate.now()) || (lesson.date().equals(LocalDate.now())
					&& lesson.endTime() != null && LocalTime.now().isBefore(lesson.endTime()))) {
				throw new IllegalStateException("课次尚未结束");
			}
		}
		int next = revision + 1;
		evidence.append(command.semesterCode(), command.key(), next, command.organizationId(), lesson.campusId(),
				lesson.sourceEntryId(), lesson.date(), minutes, reason, actor, write(lesson));
		audit.log(actor, minutes == null ? "EDU_TEACHING_HOURS_VOID" : "EDU_TEACHING_HOURS_CONFIRM",
				"term=" + command.semesterCode() + ",occurrence=" + command.key() + ",revision=" + next);
		return next;
	}

	private List<Lesson> lessons(Context ctx, LocalDate start, LocalDate end) {
		String code = ctx.term().getTermCode();
		var published = versions.latestPublishedEntries(code);
		if (published.isEmpty()) return List.of();
		Map<String, CourseOffering> offerings = scheduling.offerings(code).stream()
				.filter(item -> item.getCampusId() != null && ctx.campuses().contains(item.getCampusId()))
				.collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
		Map<String, ScheduleDateException> changes = calendar.exceptionHistory(code).stream()
				.collect(Collectors.toMap(ScheduleDateException::getId, Function.identity()));
		var dates = new ArrayList<>(calendar.publishedOccurrencesBetween(code, start, end, published));
		var pending = calendar.publishedPendingMakeups(code, published).stream()
				.filter(item -> !item.sourceDate().isBefore(start) && !item.sourceDate().isAfter(end)).toList();
		var makeupKeys = pending.stream().map(item -> item.entry().id() + "@" + item.sourceDate())
				.collect(Collectors.toSet());
		var visibleKeys = dates.stream().map(ScheduleOccurrenceView::occurrenceKey).collect(Collectors.toSet());
		// 节假日当天没有正常发生课次，但台账需要保留待补课的原始日期。
		for (var item : pending) {
			String key = item.entry().id() + "@" + item.sourceDate();
			if (!visibleKeys.contains(key)) {
				dates.add(new ScheduleOccurrenceView(key, item.sourceDate(), item.entry(), "CANCELLED", null,
						null, "节假日停课", item.entry().periodNo(), item.entry().classroomId(), null));
			}
		}
		var substitutes = teachers.findAllById(dates.stream().filter(item -> item.substituteTeacherId() != null)
				.map(ScheduleOccurrenceView::substituteTeacherId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(item -> item.getId(), item -> item.getTeacherName()));
		Map<String, Map<Integer, BellPeriod>> bellPeriods = new HashMap<>();
		return dates.stream().filter(item -> offerings.containsKey(item.entry().offeringId()))
				.map(item -> {
					var offering = offerings.get(item.entry().offeringId());
					var timetable = bellPeriods.computeIfAbsent(offering.getCampusId(),
							campus -> periodMap(ctx.term().getId(), campus));
					return lesson(item, offering, changes.get(item.exceptionId()), timetable, substitutes, makeupKeys);
				}).toList();
	}

	private Lesson lesson(ScheduleOccurrenceView item, CourseOffering offering, ScheduleDateException change,
			Map<Integer, BellPeriod> timetable, Map<String, String> substitutes, Set<String> pending) {
		boolean incoming = Set.of("MOVED_IN", "MAKEUP").contains(item.occurrenceStatus());
		String key = incoming ? item.occurrenceKey() + "@" + item.exceptionId() : item.occurrenceKey();
		boolean stopped = Set.of("CANCELLED", "MOVED_OUT").contains(item.occurrenceStatus());
		int count = stopped ? 0 : Math.max(1, item.entry().durationPeriods() == null ? 1 : item.entry().durationPeriods());
		String teacherId = item.substituteTeacherId() == null ? offering.getTeacherId() : item.substituteTeacherId();
		String teacherName = item.substituteTeacherId() == null ? offering.getTeacherName()
				: substitutes.getOrDefault(item.substituteTeacherId(), "代课教师");
		Integer minutes = minutes(timetable, item.effectivePeriodNo(), count);
		var finalPeriod = timetable.get(item.effectivePeriodNo() + count - 1);
		String status = pending.contains(item.occurrenceKey()) && stopped ? "PENDING_MAKEUP" : item.occurrenceStatus();
		String token = UUID.nameUUIDFromBytes((key + "|" + teacherId + "|" + item.effectivePeriodNo() + "|"
				+ count + "|" + minutes + "|" + item.effectiveClassroomId() + "|" + item.exceptionId())
				.getBytes(StandardCharsets.UTF_8)).toString();
		return new Lesson(key, item.entry().id(), offering.getCampusId(), item.date(),
				change == null ? item.date() : change.getSourceDate(), offering.getId(), item.entry().teachingClassName(),
				offering.getCourseCode(), item.entry().courseName(), teacherId, teacherName, offering.getTeacherName(),
				item.effectivePeriodNo(), count, minutes, finalPeriod == null ? null : finalPeriod.getEndTime(),
				status, item.exceptionId(), item.reason(), token);
	}

	private Map<Integer, BellPeriod> periodMap(String termId, String campus) {
		return bells.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(termId, campus, "ACTIVE")
				.map(schedule -> periods.findByBellScheduleIdOrderByPeriodNo(schedule.getId()).stream()
						.filter(item -> Boolean.TRUE.equals(item.getSchedulable()))
						.collect(Collectors.toMap(BellPeriod::getPeriodNo, Function.identity())))
				.orElseGet(Map::of);
	}

	private Integer minutes(Map<Integer, BellPeriod> timetable, int first, int count) {
		int minutes = 0;
		for (int number = first; number < first + count; number++) {
			BellPeriod period = timetable.get(number);
			if (period == null) return null;
			minutes += (int) Duration.between(period.getStartTime(), period.getEndTime()).toMinutes();
		}
		return minutes;
	}

	private record Context(AcademicTerm term, Set<String> campuses) {
	}

	private Context context(String organization, String termCode, String actor) {
		if (organization == null || organization.isBlank() || termCode == null || termCode.isBlank()) {
			throw new IllegalArgumentException("请选择机构和学期");
		}
		var campuses = suspensions.campuses(scopes.resolve(actor), organization, termCode);
		return new Context(terms.findByTermCode(termCode).orElseThrow(), campuses);
	}

	private void validateRange(AcademicTerm term, LocalDate start, LocalDate end) {
		if (start == null || end == null || end.isBefore(start) || start.isBefore(term.getStartDate())
				|| end.isAfter(term.getEndDate()) || end.isAfter(start.plusDays(30))) {
			throw new IllegalArgumentException("请选择学期内连续不超过 31 天的日期范围");
		}
	}

	private boolean matches(String requested, String value) {
		return requested == null || requested.isBlank() || requested.equals(value);
	}

	private Row historical(TeachingHoursEvidenceStore.Evidence value) {
		return row(read(value.snapshot()), value, value.actualMinutes(),
				value.actualMinutes() == null ? "VOIDED" : "HISTORICAL", true);
	}

	private Row row(Lesson lesson, TeachingHoursEvidenceStore.Evidence value, Integer actual, String status,
			boolean historical) {
		return new Row(lesson, actual, value == null ? 0 : value.revision(), status, historical);
	}

	private String status(Lesson lesson, TeachingHoursEvidenceStore.Evidence value) {
		if (value != null) return value.actualMinutes() == null ? "VOIDED" : "CONFIRMED";
		return lesson.plannedPeriods() == 0 ? "NO_LESSON" : "PENDING";
	}

	private String write(Lesson lesson) {
		try {
			return mapper.writeValueAsString(lesson);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("无法保存课次凭证", exception);
		}
	}

	private Lesson read(String snapshot) {
		try {
			return mapper.readValue(snapshot, Lesson.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("课次凭证无法读取", exception);
		}
	}
}
