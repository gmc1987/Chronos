package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.TeachingClassMember;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassSchedulingServiceTest {
	private CourseOfferingRepository offerings;
	private ClassroomRepository classrooms;
	private ScheduleEntryRepository entries;
	private TeachingClassMemberRepository members;
	private ClassSchedulingService service;

	@BeforeEach
	void setUp() {
		offerings = mock(CourseOfferingRepository.class);
		classrooms = mock(ClassroomRepository.class);
		entries = mock(ScheduleEntryRepository.class);
		members = mock(TeachingClassMemberRepository.class);
		service = new ClassSchedulingService(
				offerings,
				classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				entries,
				mock(TeacherTimeConstraintRepository.class),
				members,
				mock(StudentProfileRepository.class),
				mock(AcademicCalendarService.class));
	}

	@Test
	void rejectsOverlappingClassesWithSharedStudent() {
		CourseOffering target = new CourseOffering();
		target.setId("combined-offering");
		target.setTeacherId("teacher-a");
		target.setStudentCount(2);
		CourseOffering occupied = new CourseOffering();
		occupied.setId("other-offering");
		occupied.setTeacherId("teacher-b");
		Classroom room = new Classroom();
		room.setId("room-a");
		room.setCapacity(50);
		room.setEnabled(true);
		ScheduleEntry existing = new ScheduleEntry();
		existing.setOfferingId(occupied.getId());
		existing.setClassroomId("room-b");
		existing.setWeekPattern("ALL");
		TeachingClassMember targetMember = member(target.getId(), "student-a");
		TeachingClassMember otherMember = member(occupied.getId(), "student-a");
		when(offerings.findById(target.getId())).thenReturn(Optional.of(target));
		when(offerings.findById(occupied.getId())).thenReturn(Optional.of(occupied));
		when(classrooms.findById(room.getId())).thenReturn(Optional.of(room));
		when(entries.findOverlapping(
				"2026-FALL", 1, 1, 1, 1, 20, null))
				.thenReturn(List.of(existing));
		when(members.findByOfferingIdOrderByCreateTime(target.getId()))
				.thenReturn(List.of(targetMember));
		when(members.findByOfferingIdOrderByCreateTime(occupied.getId()))
				.thenReturn(List.of(otherMember));

		ScheduleEntryCommand command = new ScheduleEntryCommand(
				"2026-FALL", target.getId(), room.getId(),
				1, 1, 1, "ALL", 1, 20, false, null);
		assertThatThrownBy(() -> service.saveEntry(null, command))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("学生在相同时间已有课程");
	}

	private TeachingClassMember member(String offeringId, String studentId) {
		TeachingClassMember value = new TeachingClassMember();
		value.setOfferingId(offeringId);
		value.setStudentId(studentId);
		value.setEnrollmentStatus("ENROLLED");
		return value;
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
				false,
				null);

		assertThatThrownBy(() -> service.saveEntry(null, command))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("教学任务和教室必须属于同一校区");
	}
}
