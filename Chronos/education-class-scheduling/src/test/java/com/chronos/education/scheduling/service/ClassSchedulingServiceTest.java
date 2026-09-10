package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassSchedulingServiceTest {
	private CourseOfferingRepository offerings;
	private ClassroomRepository classrooms;
	private ClassSchedulingService service;

	@BeforeEach
	void setUp() {
		offerings = mock(CourseOfferingRepository.class);
		classrooms = mock(ClassroomRepository.class);
		service = new ClassSchedulingService(
				offerings,
				classrooms,
				mock(ScheduleEntryRepository.class),
				mock(TeacherTimeConstraintRepository.class),
				mock(TeachingClassMemberRepository.class),
				mock(StudentProfileRepository.class));
	}

	@Test
	void rejectsSchedulingOfferingIntoClassroomOfAnotherCampus() {
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-campus-a");
		offering.setCampusId("campus-a");
		Classroom classroom = new Classroom();
		classroom.setId("room-campus-b");
		classroom.setCampusId("campus-b");
		when(offerings.findById(offering.getId()))
				.thenReturn(Optional.of(offering));
		when(classrooms.findById(classroom.getId()))
				.thenReturn(Optional.of(classroom));

		ScheduleEntryCommand command = new ScheduleEntryCommand(
				"2026-FALL",
				offering.getId(),
				classroom.getId(),
				1,
				1,
				1,
				"ALL",
				1,
				20,
				false);

		assertThatThrownBy(() -> service.saveEntry(null, command))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("教学任务和教室必须属于同一校区");
	}
}
