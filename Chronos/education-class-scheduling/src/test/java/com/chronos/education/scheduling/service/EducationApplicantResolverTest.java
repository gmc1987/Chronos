package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.model.pojo.AdminUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EducationApplicantResolverTest {
	private final EducationUserBindingRepository bindings = mock(EducationUserBindingRepository.class);
	private final IAdminUserRepository users = mock(IAdminUserRepository.class);
	private final TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
	private final EducationApplicantResolver resolver = new EducationApplicantResolver(bindings, users, teachers);

	@Test
	void resolvesExplicitEducationProfileBindingFirst() {
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileType("STUDENT");
		binding.setProfileId("student-1");
		when(bindings.findByUsernameAndStatusOrderByProfileType("student", "ACTIVE"))
				.thenReturn(List.of(binding));

		assertThat(resolver.resolve("student", "STUDENT")).isEqualTo("student-1");
	}

	@Test
	void resolvesLegacyTeacherAccountThroughEmployeeProfile() {
		AdminUser account = new AdminUser();
		account.setEmployeeId("employee-1");
		TeacherAcademicProfile teacher = new TeacherAcademicProfile();
		teacher.setId("teacher-1");
		when(bindings.findByUsernameAndStatusOrderByProfileType("teacher", "ACTIVE"))
				.thenReturn(List.of());
		when(users.findByUsername("teacher")).thenReturn(account);
		when(teachers.findByEmployeeId("employee-1")).thenReturn(Optional.of(teacher));

		assertThat(resolver.resolve("teacher", "TEACHER")).isEqualTo("teacher-1");
	}

	@Test
	void rejectsAccountWithoutRequiredEducationProfile() {
		when(bindings.findByUsernameAndStatusOrderByProfileType("unknown", "ACTIVE"))
				.thenReturn(List.of());

		assertThatThrownBy(() -> resolver.resolve("unknown", "STUDENT"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("未绑定有效的 STUDENT 教务档案");
	}
}
