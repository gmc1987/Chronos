package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.Role;
import com.chronos.service.iService.IRefreshTokenService;
import com.chronos.service.impl.StaffAccountProvisioningService;
import com.chronos.service.impl.UserRoleAssignmentService;

class StaffAccountProvisioningServiceTest {
	@Test
	void shouldNotHashTemporaryPasswordsDuringCandidateValidation() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		UserRoleAssignmentService roleAssignments = mock(UserRoleAssignmentService.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		StaffAccountProvisioningService service = new StaffAccountProvisioningService(
				users, roleAssignments, passwordEncoder,
				mock(IRefreshTokenService.class));

		Employee employee = employee();
		Role baseRole = role("base-role", UserRoleAssignmentService.BASE_ROLE_CODE);
		when(users.findByEmployeeId(employee.getId())).thenReturn(Optional.empty());
		when(users.findByUsername(employee.getEmployeeCode())).thenReturn(null);
		when(users.save(any(AdminUser.class))).thenAnswer(invocation -> {
			AdminUser account = invocation.getArgument(0);
			account.setId("candidate-user");
			return account;
		});
		when(roleAssignments.ensureBaseAccountRole(any(AdminUser.class))).thenReturn(baseRole);

		var result = service.sync(employee, true, true);

		assertThat(result.created()).isTrue();
		assertThat(result.temporaryPassword()).isNull();
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void shouldAlwaysEnsureBaseRoleAndPreserveExistingBusinessRoles() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		UserRoleAssignmentService roleAssignments = mock(UserRoleAssignmentService.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		StaffAccountProvisioningService service = new StaffAccountProvisioningService(
				users, roleAssignments, passwordEncoder,
				mock(IRefreshTokenService.class));

		Employee employee = employee();
		AdminUser account = new AdminUser();
		account.setId("user-1");
		account.setEmployeeId(employee.getId());
		account.setUsername(employee.getEmployeeCode());
		account.setPassword("existing-password-hash");
		account.setRoles(new HashSet<>(List.of(role("business-role", "FACULTY_TEACHER"))));
		Role baseRole = role("base-role", UserRoleAssignmentService.BASE_ROLE_CODE);
		when(users.findByEmployeeId(employee.getId())).thenReturn(Optional.of(account));
		when(users.save(any(AdminUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(roleAssignments.ensureBaseAccountRole(account)).thenReturn(baseRole);

		var result = service.sync(employee, true, false);

		assertThat(account.getRoles()).extracting(Role::getRoleCode)
				.containsExactly("FACULTY_TEACHER");
		assertThat(result.roleCodes()).containsExactly(UserRoleAssignmentService.BASE_ROLE_CODE);
		assertThat(account.getPassword()).isEqualTo("existing-password-hash");
		assertThat(result.temporaryPassword()).isNull();
		verify(roleAssignments).ensureBaseAccountRole(account);
		verify(passwordEncoder, never()).encode(any());
	}

	private Employee employee() {
		Employee employee = new Employee();
		employee.setId("employee-1");
		employee.setEmployeeCode("202600001");
		employee.setEmployeeName("王老师");
		employee.setEmploymentStatus("ACTIVE");
		return employee;
	}

	private Role role(String id, String code) {
		Role role = new Role();
		role.setId(id);
		role.setRoleCode(code);
		role.setStatus(1);
		return role;
	}
}
