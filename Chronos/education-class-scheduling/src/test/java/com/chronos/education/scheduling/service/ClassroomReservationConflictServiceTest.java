package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomReservationRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassroomReservationConflictServiceTest {
	private AcademicTermRepository terms;
	private ClassroomRepository classrooms;
	private ClassroomReservationRepository reservations;
	private ClassroomUnavailableSlotRepository unavailableSlots;
	private ScheduleOccurrenceService occurrences;
	private ExamResourceReservationService examReservations;
	private ClassroomReservationConflictService service;

	@BeforeEach
	void setUp() {
		terms = mock(AcademicTermRepository.class);
		classrooms = mock(ClassroomRepository.class);
		reservations = mock(ClassroomReservationRepository.class);
		unavailableSlots = mock(ClassroomUnavailableSlotRepository.class);
		occurrences = mock(ScheduleOccurrenceService.class);
		examReservations = mock(ExamResourceReservationService.class);
		service = new ClassroomReservationConflictService(
				terms,
				classrooms,
				unavailableSlots,
				reservations,
				occurrences,
				examReservations);

		AcademicTerm term = new AcademicTerm();
		term.setTermCode("2026-FALL");
		term.setStartDate(LocalDate.of(2026, 9, 1));
		term.setEndDate(LocalDate.of(2027, 1, 20));
		when(terms.findByTermCode("2026-FALL")).thenReturn(Optional.of(term));

		Classroom classroom = new Classroom();
		classroom.setId("room-1");
		classroom.setEnabled(true);
		classroom.setCapacity(50);
		when(classrooms.findById("room-1")).thenReturn(Optional.of(classroom));
		when(unavailableSlots
				.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc("2026-FALL"))
				.thenReturn(List.of());
	}

	@Test
	void publishedCourseOccurrenceBlocksOverlappingReservation() {
		LocalDate date = LocalDate.of(2026, 9, 15);
		when(reservations.findActiveOverlapping("room-1", date, 2, 3, null))
				.thenReturn(List.of());
		ScheduleEntryView entry = new ScheduleEntryView(
				"entry-1",
				"2026-FALL",
				"offering-1",
				"O-1",
				"语文",
				"教学班一",
				"张老师",
				"room-1",
				"101教室",
				2,
				3,
				2,
				"ALL",
				1,
				20,
				"PUBLISHED",
				true,
				0L);
		when(occurrences.occurrences("2026-FALL", date)).thenReturn(List.of(
				new ScheduleOccurrenceView(
						"entry-1@2026-09-15",
						date,
						entry,
						"SCHEDULED",
						null,
						null,
						null,
						3,
						"room-1",
						null)));

		assertThatThrownBy(() -> service.assertAvailable(
				"2026-FALL",
				"room-1",
				date,
				2,
				2,
				40,
				null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("所选时段教室已有课程安排");
	}

	@Test
	void capacityIsEnforcedBeforeApprovalStarts() {
		assertThatThrownBy(() -> service.assertAvailable(
				"2026-FALL",
				"room-1",
				LocalDate.of(2026, 9, 15),
				1,
				1,
				51,
				null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("使用人数超过教室容量或填写不正确");
	}
}
