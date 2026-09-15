package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamInvigilationRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;

import lombok.RequiredArgsConstructor;

/** 已发布考试对课表和日期调整的反向占用检查。 */
@Service
@RequiredArgsConstructor
public class ExamResourceReservationService {
	private final ExamPlanRepository plans;
	private final ExamSessionRepository sessions;
	private final ExamRoomRepository rooms;
	private final ExamCandidateRepository candidates;
	private final ExamInvigilationRepository invigilators;
	private final AcademicTermRepository terms;
	private final BellScheduleRepository bellSchedules;
	private final BellPeriodRepository bellPeriods;

	/** 周课表会覆盖一个日期区间，逐个已发布考试日期验证实际发生的周次。 */
	@Transactional(readOnly = true)
	public void assertWeeklyCourseAvailable(
			String semesterCode,
			String campusId,
			Integer dayOfWeek,
			Integer periodNo,
			Integer durationPeriods,
			Integer startWeek,
			Integer endWeek,
			String weekPattern,
			String classroomId,
			String teacherId,
			Set<String> studentIds) {
		AcademicTerm term = term(semesterCode);
		for (ExamSession exam : sessions.findByExamDateBetweenAndStatus(
				term.getStartDate(), term.getEndDate(), "PUBLISHED")) {
			if (!semesterCode.equals(planSemester(exam))
					|| exam.getExamDate().getDayOfWeek().getValue() != dayOfWeek) {
				continue;
			}
			int week = (int) (ChronoUnit.DAYS.between(
					term.getStartDate(), exam.getExamDate()) / 7) + 1;
			if (week < startWeek || week > endWeek || !weekMatches(weekPattern, week)) {
				continue;
			}
			assertAvailableAt(
					term, campusId, exam, periodNo, durationPeriods,
					classroomId, teacherId, studentIds);
		}
	}

	/** 临时调课、补课、代课只校验目标日期，不影响周课表定义。 */
	@Transactional(readOnly = true)
	public void assertDatedCourseAvailable(
			String semesterCode,
			String campusId,
			LocalDate date,
			Integer periodNo,
			Integer durationPeriods,
			String classroomId,
			String teacherId,
			Set<String> studentIds) {
		AcademicTerm term = term(semesterCode);
		for (ExamSession exam : sessions.findByExamDateBetweenAndStatus(
				date, date, "PUBLISHED")) {
			if (semesterCode.equals(planSemester(exam))) {
				assertAvailableAt(
						term, campusId, exam, periodNo, durationPeriods,
						classroomId, teacherId, studentIds);
			}
		}
	}

	private void assertAvailableAt(
			AcademicTerm term,
			String campusId,
			ExamSession exam,
			Integer periodNo,
			Integer durationPeriods,
			String classroomId,
			String teacherId,
			Set<String> studentIds) {
		if (!clockTimeOverlaps(
				term, campusId, periodNo, durationPeriods,
				exam.getStartTime(), exam.getEndTime())) {
			return;
		}
		for (ExamRoom room : rooms.findBySessionId(exam.getId())) {
			if (classroomId.equals(room.getClassroomId())) {
				throw new IllegalStateException("课程与已发布考试占用的教室冲突");
			}
			if (invigilators.findByRoomIdAndStatus(room.getId(), "ASSIGNED")
					.stream().anyMatch(item -> Objects.equals(
							teacherId, item.getTeacherId()))) {
				throw new IllegalStateException("授课教师已有监考任务");
			}
			if (!studentIds.isEmpty() && candidates
					.findByRoomIdOrderBySeatNoAsc(room.getId())
					.stream().anyMatch(item -> studentIds.contains(item.getStudentId()))) {
				throw new IllegalStateException("课程学生已有考试安排");
			}
		}
	}

	private boolean clockTimeOverlaps(
			AcademicTerm term,
			String campusId,
			Integer periodNo,
			Integer durationPeriods,
			LocalTime examStart,
			LocalTime examEnd) {
		List<BellSchedule> options = bellSchedules
				.findByAcademicTermIdOrderByScheduleName(term.getId())
				.stream()
				.filter(item -> campusId == null || campusId.equals(item.getCampusId()))
				.toList();
		if (options.isEmpty()) {
			throw new IllegalStateException("没有校区作息时间，无法核对考试资源占用");
		}
		for (BellSchedule option : options) {
			Map<Integer, BellPeriod> periods = bellPeriods
					.findByBellScheduleIdOrderByPeriodNo(option.getId())
					.stream()
					.collect(Collectors.toMap(BellPeriod::getPeriodNo, Function.identity()));
			BellPeriod first = periods.get(periodNo);
			BellPeriod last = periods.get(periodNo + Math.max(1, durationPeriods) - 1);
			if (first == null || last == null) {
				throw new IllegalStateException("课程节次缺少作息时间，无法校验考试冲突");
			}
			if (first.getStartTime().isBefore(examEnd)
					&& examStart.isBefore(last.getEndTime())) {
				return true;
			}
		}
		return false;
	}

	private String planSemester(ExamSession exam) {
		return plans.findById(exam.getPlanId()).orElseThrow().getSemesterCode();
	}

	private AcademicTerm term(String semesterCode) {
		return terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
	}

	private boolean weekMatches(String pattern, int week) {
		return "ALL".equals(pattern)
				|| ("ODD".equals(pattern) && week % 2 == 1)
				|| ("EVEN".equals(pattern) && week % 2 == 0);
	}
}
