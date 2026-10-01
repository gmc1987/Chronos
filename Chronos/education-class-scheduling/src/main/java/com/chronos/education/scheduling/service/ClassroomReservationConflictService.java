package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomReservationRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 汇总课表、考试、停用时段和已审批申请，形成统一教室冲突判定。 */
@Service
public class ClassroomReservationConflictService {
	private final AcademicTermRepository terms;
	private final ClassroomRepository classrooms;
	private final ClassroomUnavailableSlotRepository unavailableSlots;
	private final ClassroomReservationRepository reservations;
	private final ScheduleOccurrenceService occurrences;
	private final ExamResourceReservationService examReservations;

	public ClassroomReservationConflictService(
			AcademicTermRepository terms,
			ClassroomRepository classrooms,
			ClassroomUnavailableSlotRepository unavailableSlots,
			ClassroomReservationRepository reservations,
			ScheduleOccurrenceService occurrences,
			ExamResourceReservationService examReservations) {
		this.terms = terms;
		this.classrooms = classrooms;
		this.unavailableSlots = unavailableSlots;
		this.reservations = reservations;
		this.occurrences = occurrences;
		this.examReservations = examReservations;
	}

	@Transactional(readOnly = true)
	public void assertAvailable(
			String semesterCode,
			String classroomId,
			LocalDate usageDate,
			int startPeriod,
			int durationPeriods,
			int attendeeCount,
			String excludedReservationId) {
		AcademicTerm term = terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (usageDate.isBefore(term.getStartDate()) || usageDate.isAfter(term.getEndDate())) {
			throw new IllegalArgumentException("使用日期不在所选学期范围内");
		}
		if (startPeriod < 1 || durationPeriods < 1) {
			throw new IllegalArgumentException("开始节次和持续节数必须大于零");
		}
		Classroom classroom = classrooms.findById(classroomId)
				.orElseThrow(() -> new IllegalArgumentException("教室不存在"));
		if (!Boolean.TRUE.equals(classroom.getEnabled())) {
			throw new IllegalStateException("教室已停用");
		}
		if (attendeeCount < 1 || attendeeCount > classroom.getCapacity()) {
			throw new IllegalArgumentException("使用人数超过教室容量或填写不正确");
		}

		int endPeriod = startPeriod + durationPeriods - 1;
		if (!reservations.findActiveOverlapping(
				classroomId,
				usageDate,
				startPeriod,
				endPeriod,
				excludedReservationId).isEmpty()) {
			throw new IllegalStateException("所选时段已有审批通过的教室申请");
		}

		boolean unavailable = unavailableSlots
				.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc(semesterCode)
				.stream()
				.filter(slot -> "ACTIVE".equals(slot.getStatus()))
				.filter(slot -> classroomId.equals(slot.getClassroomId()))
				.anyMatch(slot -> usageDate.getDayOfWeek().getValue() == slot.getDayOfWeek()
						&& overlaps(startPeriod, durationPeriods, slot.getStartPeriod(), slot.getEndPeriod()));
		if (unavailable) {
			throw new IllegalStateException("教室在所选时段已停用或维修");
		}

		for (ScheduleOccurrenceView occurrence : occurrences.occurrences(semesterCode, usageDate)) {
			if (Set.of("CANCELLED", "MOVED_OUT").contains(occurrence.occurrenceStatus())) {
				continue;
			}
			if (classroomId.equals(occurrence.effectiveClassroomId())
					&& overlaps(
							startPeriod,
							durationPeriods,
							occurrence.effectivePeriodNo(),
							occurrence.effectivePeriodNo()
									+ occurrence.entry().durationPeriods()
									- 1)) {
				throw new IllegalStateException("所选时段教室已有课程安排");
			}
		}

		// 复用考试中心的时钟区间换算，防止考试时间与节次边界不一致时漏判。
		examReservations.assertDatedCourseAvailable(
				semesterCode,
				classroom.getCampusId(),
				usageDate,
				startPeriod,
				durationPeriods,
				classroomId,
				null,
				Set.of());
	}

	private boolean overlaps(
			int startPeriod,
			int durationPeriods,
			int occupiedStart,
			int occupiedEnd) {
		int requestedEnd = startPeriod + durationPeriods - 1;
		return startPeriod <= occupiedEnd && requestedEnd >= occupiedStart;
	}
}
