package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EducationApplicantResolverTest {
	private final EducationUserBindingRepository bindings = mock(EducationUserBindingRepository.class);
	private final StudentGuardianRepository guardians = mock(StudentGuardianRepository.class);
	private final EducationApplicantResolver resolver = new EducationApplicantResolver(bindings, guardians);

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
	void revokedTeacherBindingCannotResolveApplicantFromEmployeeProfile() {
		when(bindings.findByUsernameAndStatusOrderByProfileType("teacher", "ACTIVE"))
				.thenReturn(List.of());

		assertThatThrownBy(() -> resolver.resolve("teacher", "TEACHER"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("未绑定有效的 TEACHER 教务档案");
	}

	@Test
	void rejectsAccountWithoutRequiredEducationProfile() {
		when(bindings.findByUsernameAndStatusOrderByProfileType("unknown", "ACTIVE"))
				.thenReturn(List.of());

		assertThatThrownBy(() -> resolver.resolve("unknown", "STUDENT"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("未绑定有效的 STUDENT 教务档案");
	}

	@Test
	void resolvesParentRequestOnlyForRelatedStudent() {
		EducationApplicantResolver parentResolver = new EducationApplicantResolver(bindings, guardians);
		EducationUserBinding parent = new EducationUserBinding();
		parent.setProfileId("parent-1");
		when(bindings.findByUsernameAndProfileTypeAndStatus("parent", "PARENT", "ACTIVE")).thenReturn(Optional.of(parent));
		when(guardians.findByStudentIdAndParentId("student-1", "parent-1"))
				.thenReturn(Optional.of(new StudentGuardianRelation()));

		assertThat(parentResolver.resolve("parent", "STUDENT", "student-1")).isEqualTo("student-1");
	}
}
