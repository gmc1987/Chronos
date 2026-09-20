package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentStatusChangeRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentStatusChange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class HeadTeacherWorkbenchServiceTest {
	private AdministrativeClassRepository classes;
	private StudentProfileRepository students;
	private StudentGuardianRepository guardians;
	private StudentStatusChangeRepository statusChanges;
	private EducationUserBindingRepository bindings;
	private HeadTeacherWorkbenchService service;

	@BeforeEach
	void setUp() {
		classes = mock(AdministrativeClassRepository.class);
		students = mock(StudentProfileRepository.class);
		guardians = mock(StudentGuardianRepository.class);
		statusChanges = mock(StudentStatusChangeRepository.class);
		bindings = mock(EducationUserBindingRepository.class);
		service = new HeadTeacherWorkbenchService(
				mock(IAdminUserRepository.class),
				mock(TeacherAcademicProfileRepository.class),
				bindings,
				classes,
				students,
				guardians,
				statusChanges);
	}

	@Test
	void detailAggregatesOnlyHeadTeachersOwnClass() {
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileType("TEACHER");
		binding.setProfileId("teacher-1");
		when(bindings.findByUsernameAndStatusOrderByProfileType("head", "ACTIVE"))
				.thenReturn(List.of(binding));

		AdministrativeClass value = new AdministrativeClass();
		ReflectionTestUtils.setField(value, "id", "class-1");
		value.setClassCode("2026-01");
		value.setClassName("2026级一班");
		when(classes.findByHeadTeacherId("teacher-1")).thenReturn(List.of(value));

		StudentProfile student = new StudentProfile();
		ReflectionTestUtils.setField(student, "id", "student-1");
		student.setStudentNo("S001");
		student.setStudentName("学生甲");
		student.setEnrollmentStatus("ACTIVE");
		when(students.findByAdministrativeClassId("class-1")).thenReturn(List.of(student));

		StudentGuardianRelation guardian = new StudentGuardianRelation();
		guardian.setStudentId("student-1");
		guardian.setPrimaryGuardian(true);
		when(guardians.findByStudentIdIn(List.of("student-1")))
				.thenReturn(List.of(guardian));

		StudentStatusChange change = new StudentStatusChange();
		ReflectionTestUtils.setField(change, "id", "change-1");
		change.setStudentId("student-1");
		change.setChangeType("SUSPEND");
		change.setEffectiveDate(LocalDate.of(2026, 9, 22));
		change.setReason("健康原因");
		change.setRequestedAt(LocalDateTime.of(2026, 9, 19, 9, 0));
		when(statusChanges.findTop20ByStudentIdInAndStatusOrderByRequestedAtDesc(
				List.of("student-1"),
				"PENDING"))
				.thenReturn(List.of(change));

		Map<String, Object> result = service.classDetail("head", "class-1");

		@SuppressWarnings("unchecked")
		Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
		assertThat(metrics.get("studentCount")).isEqualTo(1);
		assertThat(metrics.get("guardianMissingCount")).isEqualTo(0L);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> pending = (List<Map<String, Object>>) result.get("pendingChanges");
		assertThat(pending).singleElement()
				.extracting(item -> item.get("studentName"))
				.isEqualTo("学生甲");
	}

	@Test
	void detailRejectsClassOutsideCurrentHeadTeacherScope() {
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileType("TEACHER");
		binding.setProfileId("teacher-1");
		when(bindings.findByUsernameAndStatusOrderByProfileType("head", "ACTIVE"))
				.thenReturn(List.of(binding));
		when(classes.findByHeadTeacherId("teacher-1")).thenReturn(List.of());

		assertThatThrownBy(() -> service.classDetail("head", "class-other"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权查看该班级工作台");
	}
}
