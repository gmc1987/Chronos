package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.service.iService.IDataScopeService;
import com.chronos.model.vo.DataScopeContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class EducationDataScopeServiceTest {
	private AdministrativeClassRepository classes;
	private StudentProfileRepository students;
	private IDataScopeService platformScopes;
	private CourseOfferingRepository offerings;
	private ClassroomRepository classrooms;
	private EducationDataScopeService service;

	@BeforeEach
	void setUp() {
		classes = mock(AdministrativeClassRepository.class);
		students = mock(StudentProfileRepository.class);
		platformScopes = mock(IDataScopeService.class);
		offerings = mock(CourseOfferingRepository.class);
		classrooms = mock(ClassroomRepository.class);
		service = new EducationDataScopeService(
				platformScopes,
				mock(TeacherAcademicProfileRepository.class),
				mock(TeacherTeachingAssignmentRepository.class),
				classes,
				students,
				mock(StudentGuardianRepository.class),
				offerings,
				classrooms,
				mock(ScheduleEntryRepository.class));
	}

	@Test
	void resolvesCampusOrganizationIntoVisibleClasses() {
		AdministrativeClass administrativeClass = new AdministrativeClass();
		administrativeClass.setId("class-campus-1");
		administrativeClass.setCampusId("campus-1");
		when(platformScopes.resolve("campus.manager")).thenReturn(new DataScopeContext(
				false,
				null,
				Set.of("campus-1"),
				Set.of(),
				Set.of(),
				Set.of("CUSTOM_ORGANIZATION"),
				Map.of()));
		when(classes.findByCampusIdIn(List.of("campus-1")))
				.thenReturn(List.of(administrativeClass));

		EducationDataScope scope = service.resolve("campus.manager");

		assertThat(scope.campusIds()).containsExactly("campus-1");
		assertThat(scope.administrativeClassIds()).containsExactly("class-campus-1");
	}

	@Test
	void rejectsAllScheduleDimensionForRestrictedScope() {
		EducationDataScope scope = scope(Set.of("grade-1"), Set.of("class-1"));

		assertThatThrownBy(() -> service.assertScheduleDimensionAccess(
				scope,
				"ALL",
				null))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权按该维度查询全校课表");
	}

	@Test
	void rejectsStudentOutsideAuthorizedClassAndGrade() {
		StudentProfile student = student("student-2", "class-2", "grade-2");
		when(students.findById("student-2")).thenReturn(Optional.of(student));

		EducationDataScope scope = scope(Set.of("grade-1"), Set.of("class-1"));

		assertThatThrownBy(() -> service.assertStudentAccess(scope, "student-2"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权访问该学生数据");
	}

	@Test
	void allowsStudentInAuthorizedGrade() {
		StudentProfile student = student("student-1", "class-2", "grade-1");
		when(students.findById("student-1")).thenReturn(Optional.of(student));

		EducationDataScope scope = scope(Set.of("grade-1"), Set.of());

		assertThatCode(() -> service.assertStudentAccess(scope, "student-1"))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsMovingClassOutsideAuthorizedGrade() {
		AdministrativeClass administrativeClass = new AdministrativeClass();
		administrativeClass.setId("class-1");
		administrativeClass.setGradeId("grade-1");
		when(classes.findById("class-1"))
				.thenReturn(Optional.of(administrativeClass));

		EducationDataScope scope = scope(Set.of("grade-1"), Set.of("class-1"));

		assertThatThrownBy(() -> service.assertClassUpdate(
				scope,
				"class-1",
				"grade-2"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权将班级移动到该年级");
	}

	@Test
	void campusManagerOnlySeesOfferingsAndClassroomsFromOwnCampus() {
		EducationDataScope scope = new EducationDataScope(
				false,
				Set.of("campus-1"),
				Set.of(),
				Set.of(),
				Set.of());
		CourseOffering ownOffering = offering("offering-1", "campus-1");
		CourseOffering otherOffering = offering("offering-2", "campus-2");
		Classroom ownRoom = classroom("room-1", "campus-1");
		Classroom otherRoom = classroom("room-2", "campus-2");

		assertThat(service.visibleOfferings(scope, List.of(ownOffering, otherOffering)))
				.containsExactly(ownOffering);
		assertThat(service.visibleClassrooms(scope, List.of(ownRoom, otherRoom)))
				.containsExactly(ownRoom);
	}

	@Test
	void rejectsKnownOfferingAndClassroomFromAnotherCampus() {
		EducationDataScope scope = new EducationDataScope(
				false,
				Set.of("campus-1"),
				Set.of(),
				Set.of(),
				Set.of());
		when(offerings.findById("offering-2"))
				.thenReturn(Optional.of(offering("offering-2", "campus-2")));
		when(classrooms.findById("room-2"))
				.thenReturn(Optional.of(classroom("room-2", "campus-2")));

		assertThatThrownBy(() -> service.assertOfferingAccess(scope, "offering-2"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权访问该教学任务");
		assertThatThrownBy(() -> service.assertClassroomAccess(scope, "room-2"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权访问该教室");
	}

	@Test
	void campusManagerCanOnlyAccessTeachersOfferingCoursesOnOwnCampus() {
		EducationDataScope scope = new EducationDataScope(
				false,
				Set.of("campus-1"),
				Set.of(),
				Set.of(),
				Set.of());
		when(offerings.existsByTeacherIdAndCampusIdIn("teacher-1", List.of("campus-1")))
				.thenReturn(true);

		assertThat(service.canAccessTeacher(scope, "teacher-1")).isTrue();
		assertThat(service.canAccessTeacher(scope, "teacher-2")).isFalse();
	}

	private EducationDataScope scope(Set<String> gradeIds, Set<String> classIds) {
		return new EducationDataScope(
				false,
				Set.of(),
				gradeIds,
				classIds,
				Set.of());
	}

	private StudentProfile student(String id, String classId, String gradeId) {
		StudentProfile student = new StudentProfile();
		student.setId(id);
		student.setAdministrativeClassId(classId);
		student.setGradeId(gradeId);
		return student;
	}

	private CourseOffering offering(String id, String campusId) {
		CourseOffering value = new CourseOffering();
		value.setId(id);
		value.setCampusId(campusId);
		return value;
	}

	private Classroom classroom(String id, String campusId) {
		Classroom value = new Classroom();
		value.setId(id);
		value.setCampusId(campusId);
		return value;
	}
}
