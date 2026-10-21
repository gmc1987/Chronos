package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;

@ExtendWith(MockitoExtension.class)
class StudentAccountProvisioningServiceTest {
	@Mock private IAdminUserRepository users;
	@Mock private IRoleRepository roles;
	@Mock private EducationUserBindingRepository bindings;
	@Mock private PasswordEncoder passwords;
	@InjectMocks private StudentAccountProvisioningService service;

	@Test
	void createsStudentAccountAndOneToOneProfileBinding() {
		StudentProfile student = student();
		Role role = new Role(); role.setId("student-role");
		when(bindings.findByProfileTypeAndProfileIdAndStatus("STUDENT", student.getId(), "ACTIVE"))
				.thenReturn(Optional.empty());
		when(users.findByUsername("202603000101")).thenReturn(null);
		when(roles.findByRoleCode("EDU_STUDENT")).thenReturn(role);
		when(passwords.encode(any())).thenReturn("encoded-password");

		String username = service.provision(student, "school-1");

		assertThat(username).isEqualTo("202603000101");
		ArgumentCaptor<AdminUser> account = ArgumentCaptor.forClass(AdminUser.class);
		verify(users).save(account.capture());
		assertThat(account.getValue().getAccountType()).isEqualTo("STUDENT");
		assertThat(account.getValue().getOrganizationId()).isEqualTo("school-1");
		assertThat(account.getValue().getMustChangePassword()).isTrue();
		assertThat(account.getValue().getRoles()).containsExactly(role);
		ArgumentCaptor<EducationUserBinding> binding = ArgumentCaptor.forClass(EducationUserBinding.class);
		verify(bindings).save(binding.capture());
		assertThat(binding.getValue().getProfileId()).isEqualTo(student.getId());
		assertThat(binding.getValue().getVerifiedAt()).isNotNull();
	}

	@Test
	void reusesExistingActiveBindingWithoutCreatingAnotherAccount() {
		StudentProfile student = student();
		EducationUserBinding binding = new EducationUserBinding(); binding.setUsername("202603000101");
		when(bindings.findByProfileTypeAndProfileIdAndStatus("STUDENT", student.getId(), "ACTIVE"))
				.thenReturn(Optional.of(binding));

		assertThat(service.provision(student, "school-1")).isEqualTo("202603000101");
		verify(users, never()).save(any());
		verify(bindings, never()).save(any());
	}

	private StudentProfile student() {
		StudentProfile value = new StudentProfile();
		value.setId("student-1"); value.setStudentNo("202603000101"); value.setStudentName("新生账号验收");
		value.setPhone("13800000101"); return value;
	}
}
