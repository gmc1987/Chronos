package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class EducationIdentityServiceTest {
	@Test
	void resolvesOnlyActiveTeacherProfilesForLoginAccount() {
		EducationUserBindingRepository bindings = mock(EducationUserBindingRepository.class);
		EducationUserBinding teacher = binding("TEACHER", "teacher-profile-1");
		EducationUserBinding student = binding("STUDENT", "student-profile-1");
		when(bindings.findByUsernameAndStatusOrderByProfileType("teacher.user", "ACTIVE"))
				.thenReturn(List.of(teacher, student));

		EducationIdentityService service = new EducationIdentityService(bindings);

		assertThat(service.teacherIds("teacher.user"))
				.containsExactly("teacher-profile-1");
		assertThat(service.isTeacher("teacher.user", "student-profile-1"))
				.isFalse();
	}

	private EducationUserBinding binding(String type, String profileId) {
		EducationUserBinding value = new EducationUserBinding();
		value.setProfileType(type);
		value.setProfileId(profileId);
		return value;
	}
}
