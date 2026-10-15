package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.service.iService.IDataScopeService;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.EmployeeAssignment;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class EducationDataScopeServiceTest {
	private AdministrativeClassRepository classes;
	private StudentProfileRepository students;
	private IDataScopeService platformScopes;
	private CourseOfferingRepository offerings;
	private TeachingClassMemberRepository members;
	private ClassroomRepository classrooms;
	private IOrganizationRepository organizations;
	private EducationDataScopeService service;

	@BeforeEach
	void setUp() {
		classes = mock(AdministrativeClassRepository.class);
		students = mock(StudentProfileRepository.class);
		platformScopes = mock(IDataScopeService.class);
		offerings = mock(CourseOfferingRepository.class);
		members = mock(TeachingClassMemberRepository.class);
		classrooms = mock(ClassroomRepository.class);
		organizations = mock(IOrganizationRepository.class);
		service = new EducationDataScopeService(
				platformScopes,
				mock(TeacherAcademicProfileRepository.class),
				mock(TeacherTeachingAssignmentRepository.class),
				classes,
				students,
				mock(StudentGuardianRepository.class),
				null,
				offerings,
				members,
				classrooms,
				mock(ScheduleEntryRepository.class),
				organizations);
	}

	@Test
	void revokedTeacherBindingDoesNotInheritEmployeeTeachingScope() {
		TeacherAcademicProfileRepository teacherRepository = mock(TeacherAcademicProfileRepository.class);
		EducationUserBindingRepository bindingRepository = mock(EducationUserBindingRepository.class);
		TeacherAcademicProfile teacher = new TeacherAcademicProfile();
		ReflectionTestUtils.setField(teacher, "id", "teacher-1");
		when(platformScopes.resolve("teacher")).thenReturn(new DataScopeContext(
				false, "employee-1", Set.of(), Set.of(), Set.of(), Set.of("SELF"), Map.of()));
		when(teacherRepository.findByEmployeeId("employee-1")).thenReturn(Optional.of(teacher));
		when(bindingRepository.findByUsernameAndProfileTypeAndStatus("teacher", "TEACHER", "ACTIVE"))
				.thenReturn(Optional.empty());
		EducationDataScopeService boundService = new EducationDataScopeService(
				platformScopes, teacherRepository, mock(TeacherTeachingAssignmentRepository.class),
				classes, students, mock(StudentGuardianRepository.class), bindingRepository,
				offerings, members, classrooms, mock(ScheduleEntryRepository.class), organizations);

		EducationDataScope scope = boundService.resolve("teacher");

		assertThat(scope.teacherIds()).isEmpty();
		assertThat(scope.administrativeClassIds()).isEmpty();
	}

	@Test
	void teacherMayReserveOnlyAtCurrentSchoolWithoutExpandingTeachingScope() {
		TeacherAcademicProfileRepository teacherRepository = mock(TeacherAcademicProfileRepository.class);
		EducationUserBindingRepository bindingRepository = mock(EducationUserBindingRepository.class);
		IEmployeeAssignmentRepository employeeAssignments = mock(IEmployeeAssignmentRepository.class);
		EducationDataScopeService reservationScopes = new EducationDataScopeService(
				platformScopes, teacherRepository, mock(TeacherTeachingAssignmentRepository.class),
				classes, students, mock(StudentGuardianRepository.class), bindingRepository,
				offerings, members, classrooms, mock(ScheduleEntryRepository.class),
				organizations, employeeAssignments);
		when(platformScopes.resolve("teacher")).thenReturn(new DataScopeContext(
				false, "employee-1", Set.of(), Set.of(), Set.of(), Set.of("SELF"), Map.of()));
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileId("teacher-1");
		when(bindingRepository.findByUsernameAndProfileTypeAndStatus("teacher", "TEACHER", "ACTIVE"))
				.thenReturn(Optional.of(binding));
		TeacherAcademicProfile teacher = new TeacherAcademicProfile();
		teacher.setId("teacher-1");
		teacher.setEmployeeId("employee-1");
		teacher.setEnabled(true);
		when(teacherRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));
		when(teacherRepository.findByEmployeeId("employee-1")).thenReturn(Optional.of(teacher));
		EmployeeAssignment assignment = new EmployeeAssignment();
		assignment.setOrganizationId("school-1");
		when(employeeAssignments.findCurrentPrimaryAssignment("employee-1", LocalDate.now()))
				.thenReturn(Optional.of(assignment));
		Organization school = new Organization();
		school.setId("school-1");
		school.setStatus(1);
		when(organizations.findById("school-1")).thenReturn(Optional.of(school));
		when(organizations.findByParentOrgId_Id("school-1")).thenReturn(List.of());
		Classroom own = new Classroom();
		own.setCampusId("school-1");
		Classroom other = new Classroom();
		other.setCampusId("school-2");

		assertThat(reservationScopes.canReserveClassroom("teacher", own)).isTrue();
		assertThat(reservationScopes.canReserveClassroom("teacher", other)).isFalse();
		assertThat(reservationScopes.resolve("teacher").campusIds()).isEmpty();
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
		Organization campus = new Organization();
		campus.setId("campus-1");
		campus.setOrganizationType("CAMPUS");
		Organization school = new Organization();
		school.setId("school-1");
		school.setOrganizationType("SCHOOL");
		campus.setParentOrgId(school);
		when(organizations.findById("campus-1")).thenReturn(Optional.of(campus));
		when(classes.findByCampusIdIn(List.of("campus-1")))
				.thenReturn(List.of(administrativeClass));

		EducationDataScope scope = service.resolve("campus.manager");

		assertThat(scope.schoolIds()).containsExactly("school-1");
		assertThat(scope.campusIds()).containsExactly("campus-1");
		assertThat(scope.administrativeClassIds()).containsExactly("class-campus-1");
	}

	@Test
	void schoolScopeIncludesOfferingsUsingSchoolIdAsCampusWithoutLeakingOtherSchools() {
		when(platformScopes.resolve("school.manager")).thenReturn(new DataScopeContext(
				false,
				null,
				Set.of("school-1"),
				Set.of(),
				Set.of(),
				Set.of("CUSTOM_ORGANIZATION"),
				Map.of()));
		Organization school = new Organization();
		school.setId("school-1");
		school.setOrganizationType("SCHOOL");
		when(organizations.findById("school-1")).thenReturn(Optional.of(school));
		when(organizations.findByParentOrgId_Id("school-1")).thenReturn(List.of());

		EducationDataScope scope = service.resolve("school.manager");
		CourseOffering ownOffering = offering("own", "school-1");
		CourseOffering anotherSchool = offering("other", "school-2");

		assertThat(scope.schoolIds()).containsExactly("school-1");
		assertThat(scope.campusIds()).containsExactly("school-1");
		assertThat(service.visibleOfferings(scope, List.of(ownOffering, anotherSchool)))
				.containsExactly(ownOffering);
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

	@Test
	void studentOnlySeesEnrolledOfferings() {
		EducationDataScope scope = new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of("student-1"));
		CourseOffering enrolled = offering("offering-1", "campus-1");
		CourseOffering other = offering("offering-2", "campus-1");
		TeachingClassMember member = new TeachingClassMember();
		member.setOfferingId("offering-1");
		member.setStudentId("student-1");
		member.setEnrollmentStatus("ENROLLED");
		when(members.findByOfferingIdAndStudentId("offering-1", "student-1"))
				.thenReturn(Optional.of(member));
		when(members.findByOfferingIdAndStudentId("offering-2", "student-1"))
				.thenReturn(Optional.empty());

		assertThat(service.visibleOfferings(scope, List.of(enrolled, other)))
				.containsExactly(enrolled);
	}

	@Test
	void rejectsTermWithoutVisibleCourseOffering() {
		EducationDataScope scope = new EducationDataScope(
				false, Set.of("campus-1"), Set.of(), Set.of(), Set.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("term-2"))
				.thenReturn(List.of(offering("offering-2", "campus-2")));

		assertThatThrownBy(() -> service.assertTermAccess(scope, "term-2"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权访问该学期数据");
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
