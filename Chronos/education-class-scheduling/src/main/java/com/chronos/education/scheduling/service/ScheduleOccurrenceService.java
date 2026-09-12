package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleDateExceptionRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;

/** 将周期课表展开为具体日期课表，并在读取时叠加停课、调课、代课和补课例外。 */
@Service
public class ScheduleOccurrenceService {
	private static final Set<String> TYPES = Set.of("MOVE", "CANCEL", "SUBSTITUTE", "MAKEUP");
	private final AcademicTermRepository terms;
	private final AcademicCalendarDayRepository calendarDays;
	private final ScheduleEntryRepository entries;
	private final ScheduleDateExceptionRepository exceptions;
	private final ClassSchedulingService scheduling;
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final ClassroomUnavailableSlotRepository unavailableSlots;
	private final TeacherTimeConstraintRepository teacherConstraints;
	private final TeachingClassMemberRepository teachingClassMembers;

	public ScheduleOccurrenceService(
			AcademicTermRepository terms,
			AcademicCalendarDayRepository calendarDays,
			ScheduleEntryRepository entries,
			ScheduleDateExceptionRepository exceptions,
			ClassSchedulingService scheduling,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			ClassroomUnavailableSlotRepository unavailableSlots,
			TeacherTimeConstraintRepository teacherConstraints,
			TeachingClassMemberRepository teachingClassMembers) {
		this.terms = terms;
		this.calendarDays = calendarDays;
		this.entries = entries;
		this.exceptions = exceptions;
		this.scheduling = scheduling;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.unavailableSlots = unavailableSlots;
		this.teacherConstraints = teacherConstraints;
		this.teachingClassMembers = teachingClassMembers;
	}

	@Transactional(readOnly = true)
	public List<ScheduleOccurrenceView> occurrences(String semesterCode, LocalDate date) {
		return occurrences(semesterCode, date, scheduling.schedule(semesterCode));
	}

	/** 门户传入发布快照，避免管理端尚未发布的草稿出现在个人日期课表中。 */
	@Transactional(readOnly = true)
	public List<ScheduleOccurrenceView> publishedOccurrences(
			String semesterCode,
			LocalDate date,
			List<ScheduleEntry> publishedEntries) {
		Map<String, CourseOffering> offeringById = offerings
				.findBySemesterCodeOrderByOfferingCode(semesterCode)
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, item -> item));
		Map<String, Classroom> classroomById = classrooms.findAllById(
				publishedEntries.stream()
						.map(ScheduleEntry::getClassroomId)
						.collect(Collectors.toSet()))
				.stream()
				.collect(Collectors.toMap(Classroom::getId, item -> item));
		List<ScheduleEntryView> views = publishedEntries.stream()
				.filter(item -> !"CANCELLED".equals(item.getStatus()))
				.map(item -> view(
						item,
						offeringById.get(item.getOfferingId()),
						classroomById.get(item.getClassroomId())))
				.toList();
		return occurrences(semesterCode, date, views);
	}

	private List<ScheduleOccurrenceView> occurrences(
			String semesterCode,
			LocalDate date,
			List<ScheduleEntryView> schedule) {
		AcademicTerm term = term(semesterCode);
		if (date.isBefore(term.getStartDate()) || date.isAfter(term.getEndDate())) {
			return List.of();
		}
		boolean teachingDate = calendarDays.findByAcademicTermIdAndCalendarDate(term.getId(), date)
				.map(item -> Boolean.TRUE.equals(item.getTeachingDay()))
				.orElse(true);
		List<ScheduleDateException> active = exceptions
				.findBySemesterCodeAndStatusOrderBySourceDateAsc(semesterCode, "ACTIVE");
		List<ScheduleOccurrenceView> result = new ArrayList<>();
		if (teachingDate) {
			schedule.stream()
					.filter(entry -> scheduledOn(entry, term, date))
					.map(entry -> baseOccurrence(entry, date, active))
					.forEach(result::add);
		}
		active.stream()
				.filter(item -> date.equals(item.getTargetDate()))
				.filter(item -> "MOVE".equals(item.getExceptionType())
						|| "MAKEUP".equals(item.getExceptionType()))
				.map(item -> targetOccurrence(item, schedule, date))
				.forEach(result::add);
		return result.stream()
				.sorted(Comparator.comparing(ScheduleOccurrenceView::effectivePeriodNo))
				.toList();
	}

	private ScheduleEntryView view(
			ScheduleEntry entry,
			CourseOffering offering,
			Classroom classroom) {
		return new ScheduleEntryView(
				entry.getId(),
				entry.getSemesterCode(),
				entry.getOfferingId(),
				offering == null ? "" : offering.getOfferingCode(),
				offering == null ? "未知课程" : offering.getCourseName(),
				offering == null ? "" : offering.getTeachingClassName(),
				offering == null ? "" : offering.getTeacherName(),
				entry.getClassroomId(),
				classroom == null ? "" : classroom.getRoomName(),
				entry.getDayOfWeek(),
				entry.getPeriodNo(),
				entry.getDurationPeriods(),
				entry.getWeekPattern(),
				entry.getStartWeek(),
				entry.getEndWeek(),
				entry.getStatus(),
				entry.getLocked(),
				entry.getRecordVersion());
	}

	@Transactional(readOnly = true)
	public List<ScheduleDateException> exceptions(
			String semesterCode,
			LocalDate startDate,
			LocalDate endDate) {
		return exceptions.findBySemesterCodeAndSourceDateBetweenAndStatusOrderBySourceDateAsc(
				semesterCode, startDate, endDate, "ACTIVE");
	}

	@Transactional(readOnly = true)
	public List<ScheduleDateException> exceptionHistory(String semesterCode) {
		return exceptions.findBySemesterCodeOrderBySourceDateDesc(semesterCode);
	}

	@Transactional
	public ScheduleDateException saveException(String id, ScheduleDateException command) {
		AcademicTerm term = term(command.getSemesterCode());
		ScheduleEntry source = entries.findById(command.getSourceEntryId())
				.orElseThrow(() -> new IllegalArgumentException("源课表项不存在"));
		if (!command.getSemesterCode().equals(source.getSemesterCode())) {
			throw new IllegalArgumentException("源课表项不属于当前学期");
		}
		if (!TYPES.contains(command.getExceptionType())) {
			throw new IllegalArgumentException("不支持的课表例外类型");
		}
		assertTermDate(term, command.getSourceDate(), "原上课日期");
		boolean duplicateActive = exceptions
				.findBySemesterCodeAndStatusOrderBySourceDateAsc(command.getSemesterCode(), "ACTIVE")
				.stream()
				.filter(item -> id == null || !id.equals(item.getId()))
				.anyMatch(item -> command.getSourceEntryId().equals(item.getSourceEntryId())
						&& command.getSourceDate().equals(item.getSourceDate()));
		if (duplicateActive) {
			throw new IllegalStateException("该课程日期已有生效中的调整，请先撤销或编辑原记录");
		}
		if (!scheduledOn(source, term, command.getSourceDate())) {
			throw new IllegalArgumentException("原上课日期与周期课表不匹配");
		}
		if ("MOVE".equals(command.getExceptionType()) || "MAKEUP".equals(command.getExceptionType())) {
			assertTermDate(term, command.getTargetDate(), "目标日期");
			if (command.getTargetPeriodNo() == null || command.getTargetPeriodNo() < 1) {
				throw new IllegalArgumentException("目标节次不能为空");
			}
		}
		if ("SUBSTITUTE".equals(command.getExceptionType())
				&& (command.getSubstituteTeacherId() == null
						|| command.getSubstituteTeacherId().isBlank())) {
			throw new IllegalArgumentException("代课教师不能为空");
		}
		validateConflict(command, source, term);
		ScheduleDateException value = id == null
				? new ScheduleDateException()
				: exceptions.findById(id)
						.orElseThrow(() -> new IllegalArgumentException("课表日期例外不存在"));
		value.setSemesterCode(command.getSemesterCode());
		value.setSourceEntryId(command.getSourceEntryId());
		value.setSourceDate(command.getSourceDate());
		value.setExceptionType(command.getExceptionType());
		value.setTargetDate(command.getTargetDate());
		value.setTargetPeriodNo(command.getTargetPeriodNo());
		value.setTargetClassroomId(command.getTargetClassroomId());
		value.setSubstituteTeacherId(command.getSubstituteTeacherId());
		value.setWorkflowInstanceId(command.getWorkflowInstanceId());
		value.setReason(required(command.getReason(), "变更原因不能为空"));
		value.setStatus("ACTIVE");
		return exceptions.save(value);
	}

	@Transactional
	public void cancelException(String id) {
		ScheduleDateException value = exceptions.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("课表日期例外不存在"));
		value.setStatus("CANCELLED");
		exceptions.save(value);
	}

	@Transactional
	public ScheduleDateException restoreException(String id) {
		ScheduleDateException value = exception(id);
		ScheduleEntry source = entries.findById(value.getSourceEntryId())
				.orElseThrow(() -> new IllegalArgumentException("源课表项不存在"));
		boolean duplicateActive = exceptions
				.findBySemesterCodeAndStatusOrderBySourceDateAsc(value.getSemesterCode(), "ACTIVE")
				.stream()
				.anyMatch(item -> value.getSourceEntryId().equals(item.getSourceEntryId())
						&& value.getSourceDate().equals(item.getSourceDate()));
		if (duplicateActive) {
			throw new IllegalStateException("该课程日期已有其他生效调整，不能恢复");
		}
		validateConflict(value, source, term(value.getSemesterCode()));
		value.setStatus("ACTIVE");
		return exceptions.save(value);
	}

	@Transactional(readOnly = true)
	public ScheduleDateException exception(String id) {
		return exceptions.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("课表日期例外不存在"));
	}

	private ScheduleOccurrenceView baseOccurrence(
			ScheduleEntryView entry,
			LocalDate date,
			List<ScheduleDateException> values) {
		ScheduleDateException exception = values.stream()
				.filter(item -> entry.id().equals(item.getSourceEntryId()) && date.equals(item.getSourceDate()))
				.findFirst()
				.orElse(null);
		String status = exception == null ? "SCHEDULED" : switch (exception.getExceptionType()) {
			case "CANCEL" -> "CANCELLED";
			case "MOVE" -> "MOVED_OUT";
			case "SUBSTITUTE" -> "SUBSTITUTED";
			default -> "SCHEDULED";
		};
		return occurrence(entry, date, exception, status, entry.periodNo(), entry.classroomId());
	}

	private ScheduleOccurrenceView targetOccurrence(
			ScheduleDateException exception,
			List<ScheduleEntryView> schedule,
			LocalDate date) {
		ScheduleEntryView entry = schedule.stream()
				.filter(item -> item.id().equals(exception.getSourceEntryId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("课表例外关联的源课表不存在"));
		return occurrence(
				entry,
				date,
				exception,
				"MOVE".equals(exception.getExceptionType()) ? "MOVED_IN" : "MAKEUP",
				exception.getTargetPeriodNo(),
				exception.getTargetClassroomId() == null
						? entry.classroomId()
						: exception.getTargetClassroomId());
	}

	private ScheduleOccurrenceView occurrence(
			ScheduleEntryView entry,
			LocalDate date,
			ScheduleDateException exception,
			String status,
			Integer periodNo,
			String classroomId) {
		return new ScheduleOccurrenceView(
				entry.id() + "@" + date,
				date,
				entry,
				status,
				exception == null ? null : exception.getExceptionType(),
				exception == null ? null : exception.getId(),
				exception == null ? null : exception.getReason(),
				periodNo,
				classroomId,
				exception == null ? null : exception.getSubstituteTeacherId());
	}

	/** 对日期级调整执行教室、教师和学生硬冲突校验。 */
	private void validateConflict(
			ScheduleDateException command,
			ScheduleEntry source,
			AcademicTerm term) {
		if ("CANCEL".equals(command.getExceptionType())) {
			return;
		}
		CourseOffering sourceOffering = offerings.findById(source.getOfferingId())
				.orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
		LocalDate date = command.getTargetDate() == null
				? command.getSourceDate()
				: command.getTargetDate();
		int period = command.getTargetPeriodNo() == null
				? source.getPeriodNo()
				: command.getTargetPeriodNo();
		int duration = Math.max(source.getDurationPeriods() == null ? 1 : source.getDurationPeriods(), 1);
		String classroomId = command.getTargetClassroomId() == null
				? source.getClassroomId()
				: command.getTargetClassroomId();
		String teacherId = "SUBSTITUTE".equals(command.getExceptionType())
				? command.getSubstituteTeacherId()
				: sourceOffering.getTeacherId();

		boolean teachingDate = calendarDays.findByAcademicTermIdAndCalendarDate(term.getId(), date)
				.map(day -> Boolean.TRUE.equals(day.getTeachingDay()))
				.orElse(true);
		if (!teachingDate) {
			throw new IllegalStateException("目标日期不是教学日");
		}
		Classroom classroom = classrooms.findById(classroomId)
				.orElseThrow(() -> new IllegalArgumentException("目标教室不存在"));
		if (!Boolean.TRUE.equals(classroom.getEnabled())) {
			throw new IllegalStateException("目标教室已停用");
		}
		if (classroom.getCapacity() < sourceOffering.getStudentCount()) {
			throw new IllegalStateException("目标教室容量小于教学班人数");
		}
		int weekday = date.getDayOfWeek().getValue();
		boolean unavailable = unavailableSlots
				.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc(source.getSemesterCode())
				.stream()
				.filter(slot -> "ACTIVE".equals(slot.getStatus()))
				.filter(slot -> classroomId.equals(slot.getClassroomId()))
				.anyMatch(slot -> weekday == slot.getDayOfWeek()
						&& overlaps(
								period,
								duration,
								slot.getStartPeriod(),
								slot.getEndPeriod() - slot.getStartPeriod() + 1));
		if (unavailable) {
			throw new IllegalStateException("目标教室在所选时段不可用");
		}
		boolean forbidden = teacherConstraints
				.findBySemesterCodeAndTeacherId(source.getSemesterCode(), teacherId)
				.stream()
				.filter(rule -> "FORBIDDEN".equals(rule.getConstraintType()))
				.anyMatch(rule -> weekday == rule.getDayOfWeek()
						&& overlaps(period, duration, rule.getPeriodNo(), 1));
		if (forbidden) {
			throw new IllegalStateException("目标时段命中教师禁排时间");
		}

		Set<String> sourceStudents = studentIds(source.getOfferingId());
		for (ScheduleOccurrenceView occupied : occurrences(source.getSemesterCode(), date)) {
			if (occupied.entry().id().equals(source.getId())
					&& date.equals(command.getSourceDate())) {
				continue;
			}
			if (Set.of("CANCELLED", "MOVED_OUT").contains(occupied.occurrenceStatus())
					|| !overlaps(
							period,
							duration,
							occupied.effectivePeriodNo(),
							occupied.entry().durationPeriods())) {
				continue;
			}
			CourseOffering occupiedOffering = offerings
					.findById(occupied.entry().offeringId())
					.orElse(null);
			String occupiedTeacher = occupied.substituteTeacherId() == null
					? occupiedOffering == null ? null : occupiedOffering.getTeacherId()
					: occupied.substituteTeacherId();
			if (classroomId.equals(occupied.effectiveClassroomId())) {
				throw new IllegalStateException("目标时段教室已被占用");
			}
			if (teacherId.equals(occupiedTeacher)) {
				throw new IllegalStateException("目标时段教师已有课程");
			}
			Set<String> occupiedStudents = studentIds(occupied.entry().offeringId());
			if (sourceStudents.stream().anyMatch(occupiedStudents::contains)) {
				throw new IllegalStateException("目标时段存在学生个人课表冲突");
			}
		}
	}

	private Set<String> studentIds(String offeringId) {
		return teachingClassMembers.findByOfferingIdOrderByCreateTime(offeringId)
				.stream()
				.filter(member -> "ENROLLED".equals(member.getEnrollmentStatus()))
				.map(member -> member.getStudentId())
				.collect(Collectors.toCollection(HashSet::new));
	}

	private boolean overlaps(int start, int duration, int otherStart, int otherDuration) {
		return start <= otherStart + Math.max(otherDuration, 1) - 1
				&& start + Math.max(duration, 1) - 1 >= otherStart;
	}

	private boolean scheduledOn(ScheduleEntryView entry, AcademicTerm term, LocalDate date) {
		int week = week(term, date);
		return entry.dayOfWeek() == date.getDayOfWeek().getValue()
				&& week >= entry.startWeek()
				&& week <= entry.endWeek()
				&& weekMatches(entry.weekPattern(), week);
	}

	private boolean scheduledOn(ScheduleEntry entry, AcademicTerm term, LocalDate date) {
		int week = week(term, date);
		return entry.getDayOfWeek() == date.getDayOfWeek().getValue()
				&& week >= entry.getStartWeek()
				&& week <= entry.getEndWeek()
				&& weekMatches(entry.getWeekPattern(), week);
	}

	private boolean weekMatches(String pattern, int week) {
		return "ALL".equals(pattern)
				|| ("ODD".equals(pattern) && week % 2 == 1)
				|| ("EVEN".equals(pattern) && week % 2 == 0);
	}

	private int week(AcademicTerm term, LocalDate date) {
		return (int) (ChronoUnit.DAYS.between(term.getStartDate(), date) / 7) + 1;
	}

	private AcademicTerm term(String code) {
		return terms.findByTermCode(code)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
	}

	private void assertTermDate(AcademicTerm term, LocalDate date, String label) {
		if (date == null || date.isBefore(term.getStartDate()) || date.isAfter(term.getEndDate())) {
			throw new IllegalArgumentException(label + "必须位于学期范围内");
		}
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
