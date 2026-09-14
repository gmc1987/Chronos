package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CombinedOfferingSourceClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.TeachingClassMember;

class CombinedOfferingServiceTest {
	private CourseOfferingRepository offerings;
	private CombinedOfferingSourceClassRepository sources;
	private AdministrativeClassRepository classes;
	private StudentProfileRepository students;
	private TeachingClassMemberRepository members;
	private ScheduleEntryRepository schedules;
	private ClassroomRepository classrooms;
	private AcademicDataService academicData;
	private CombinedOfferingService service;
	private CourseOffering offering;

	@BeforeEach
	void setUp() {
		offerings = mock(CourseOfferingRepository.class);
		sources = mock(CombinedOfferingSourceClassRepository.class);
		classes = mock(AdministrativeClassRepository.class);
		students = mock(StudentProfileRepository.class);
		members = mock(TeachingClassMemberRepository.class);
		schedules = mock(ScheduleEntryRepository.class);
		classrooms = mock(ClassroomRepository.class);
		academicData = mock(AcademicDataService.class);
		service = new CombinedOfferingService(
				offerings, sources, classes, students, members,
				schedules, classrooms, academicData);
		offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setCampusId("campus-1");
		offering.setStudentCount(2);
		when(offerings.findForCombinedUpdate("offering-1"))
				.thenReturn(Optional.of(offering));
		when(classes.findById("class-a"))
				.thenReturn(Optional.of(sourceClass("class-a")));
		when(classes.findById("class-b"))
				.thenReturn(Optional.of(sourceClass("class-b")));
		when(students.findByAdministrativeClassId("class-a"))
				.thenReturn(List.of(student("student-a")));
		when(students.findByAdministrativeClassId("class-b"))
				.thenReturn(List.of(student("student-b")));
	}

	@Test
	void configuresOneOfferingWithStudentsFromBothClasses() {
		var result = service.configure("offering-1", List.of("class-a", "class-b"));

		assertThat(offering.getOfferingMode()).isEqualTo("COMBINED");
		assertThat(offering.getStudentCount()).isEqualTo(2);
		assertThat(result.administrativeClassIds())
				.containsExactly("class-a", "class-b");
		verify(academicData).enrollFromCombined("offering-1", "student-a");
		verify(academicData).enrollFromCombined("offering-1", "student-b");
		verify(sources, org.mockito.Mockito.times(2)).save(any());
	}

	@Test
	void refusesToConvertAnOfferingWithManualMembers() {
		TeachingClassMember member = new TeachingClassMember();
		member.setEnrollmentStatus("ENROLLED");
		when(members.findByOfferingIdOrderByCreateTime("offering-1"))
				.thenReturn(List.of(member));

		assertThatThrownBy(() -> service.configure(
				"offering-1", List.of("class-a", "class-b")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("手工选课");
	}

	@Test
	void refusesSyncWhenScheduledRoomIsTooSmall() {
		offering.setOfferingMode("COMBINED");
		ScheduleEntry entry = new ScheduleEntry();
		entry.setClassroomId("room-1");
		Classroom room = new Classroom();
		room.setCapacity(1);
		when(sources.findByOfferingIdOrderByAdministrativeClassId("offering-1"))
				.thenReturn(List.of(sourceLink("class-a"), sourceLink("class-b")));
		when(schedules.findByOfferingId("offering-1"))
				.thenReturn(List.of(entry));
		when(classrooms.findById("room-1"))
				.thenReturn(Optional.of(room));

		assertThatThrownBy(() -> service.sync("offering-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("教室容量");
	}

	private AdministrativeClass sourceClass(String id) {
		AdministrativeClass value = new AdministrativeClass();
		value.setId(id);
		value.setCampusId("campus-1");
		value.setStatus("ACTIVE");
		return value;
	}

	private StudentProfile student(String id) {
		StudentProfile value = new StudentProfile();
		value.setId(id);
		value.setEnrollmentStatus("ACTIVE");
		return value;
	}

	private com.chronos.education.scheduling.model.CombinedOfferingSourceClass sourceLink(
			String classId) {
		var value = new com.chronos.education.scheduling.model.CombinedOfferingSourceClass();
		value.setAdministrativeClassId(classId);
		return value;
	}
}
