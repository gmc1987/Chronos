package com.chronos.education.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;

@ExtendWith(MockitoExtension.class)
class ExamResourceReservationServiceTest {
	@Mock private ExamPlanRepository plans;
	@Mock private ExamSessionRepository sessions;
	@Mock private ExamRoomRepository rooms;
	@Mock private ExamCandidateRepository candidates;
	@Mock private ExamInvigilationRepository invigilators;
	@Mock private AcademicTermRepository terms;
	@Mock private BellScheduleRepository bellSchedules;
	@Mock private BellPeriodRepository bellPeriods;
	@InjectMocks private ExamResourceReservationService service;

	@Test
	void publishedExamBlocksClassroomAtOverlappingClockTime() {
		AcademicTerm term = new AcademicTerm();
		term.setId("term-1");
		term.setTermCode("2026-FALL");
		when(terms.findByTermCode("2026-FALL")).thenReturn(Optional.of(term));

		ExamPlan plan = new ExamPlan();
		plan.setId("plan-1");
		plan.setSemesterCode("2026-FALL");
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));

		ExamSession exam = new ExamSession();
		exam.setId("session-1");
		exam.setPlanId("plan-1");
		exam.setExamDate(LocalDate.of(2026, 9, 14));
		exam.setStartTime(LocalTime.of(9, 0));
		exam.setEndTime(LocalTime.of(10, 0));
		when(sessions.findByExamDateBetweenAndStatus(
				exam.getExamDate(), exam.getExamDate(), "PUBLISHED"))
				.thenReturn(List.of(exam));

		BellSchedule bell = new BellSchedule();
		bell.setId("bell-1");
		bell.setCampusId("campus-1");
		when(bellSchedules.findByAcademicTermIdOrderByScheduleName("term-1"))
				.thenReturn(List.of(bell));
		BellPeriod period = new BellPeriod();
		period.setPeriodNo(1);
		period.setStartTime(LocalTime.of(8, 30));
		period.setEndTime(LocalTime.of(9, 30));
		when(bellPeriods.findByBellScheduleIdOrderByPeriodNo("bell-1"))
				.thenReturn(List.of(period));

		ExamRoom room = new ExamRoom();
		room.setClassroomId("classroom-1");
		when(rooms.findBySessionId(exam.getId())).thenReturn(List.of(room));

		assertThrows(IllegalStateException.class, () ->
				service.assertDatedCourseAvailable(
						"2026-FALL", "campus-1", exam.getExamDate(),
						1, 1, "classroom-1", "teacher-2", Set.of()));
	}
}
