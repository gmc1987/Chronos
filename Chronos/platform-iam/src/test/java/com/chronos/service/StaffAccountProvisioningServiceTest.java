package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IPositionRoleMappingRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IUserRoleAssignmentSourceRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.PositionRoleMapping;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.UserRoleAssignmentSource;
import com.chronos.service.iService.IRefreshTokenService;
import com.chronos.service.impl.StaffAccountProvisioningService;

class StaffAccountProvisioningServiceTest {
	@Test
	void shouldReplaceOnlyPositionManagedRolesAndPreserveManualRoles() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		IEmployeeAssignmentRepository assignments = mock(IEmployeeAssignmentRepository.class);
		IPositionRoleMappingRepository mappings = mock(IPositionRoleMappingRepository.class);
		IUserRoleAssignmentSourceRepository sources = mock(IUserRoleAssignmentSourceRepository.class);
		IRoleRepository roles = mock(IRoleRepository.class);
		StaffAccountProvisioningService service = new StaffAccountProvisioningService(
				users, assignments, mappings, sources, roles,
				mock(PasswordEncoder.class), mock(IRefreshTokenService.class));

		Employee employee = new Employee();
		employee.setId("employee-1");
		employee.setEmployeeCode("202600001");
		employee.setEmployeeName("王老师");
		employee.setEmploymentStatus("ACTIVE");
		AdminUser account = new AdminUser();
		account.setId("user-1");
		account.setEmployeeId(employee.getId());
		account.setUsername(employee.getEmployeeCode());
		Role manualRole = role("manual-role", "WORKFLOW_USER");
		Role previousPositionRole = role("old-position-role", "GENERAL_STAFF");
		Role newPositionRole = role("new-position-role", "FACULTY_TEACHER");
		account.setRoles(new HashSet<>(List.of(manualRole, previousPositionRole)));
		when(users.findByEmployeeId(employee.getId())).thenReturn(Optional.of(account));
		when(users.save(any(AdminUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sources.findByUserIdAndSourceType("user-1", "POSITION_MAPPING"))
				.thenReturn(List.of(source("old-position-role", "POSITION_MAPPING")));
		when(sources.findByUserIdAndSourceType("user-1", "MANUAL"))
				.thenReturn(List.of(source("manual-role", "MANUAL")));

		EmployeeAssignment assignment = new EmployeeAssignment();
		assignment.setPositionId("position-1");
		assignment.setOrganizationId("org-1");
		assignment.setOrganizationUnitId("department-1");
		assignment.setPrimaryAssignment(true);
		assignment.setDepartmentLeader(false);
		when(assignments.findCurrentAssignments(any(), any())).thenReturn(List.of(assignment));
		PositionRoleMapping mapping = new PositionRoleMapping();
		mapping.setId("mapping-1");
		mapping.setPositionId("position-1");
		mapping.setRoleId(newPositionRole.getId());
		mapping.setPrimaryAssignmentOnly(false);
		mapping.setDepartmentLeaderOnly(false);
		when(mappings.findByPositionIdAndStatusOrderByPriorityDesc("position-1", 1))
				.thenReturn(List.of(mapping));
		when(roles.findById(newPositionRole.getId())).thenReturn(Optional.of(newPositionRole));

		var result = service.sync(employee, true, true, false);

		assertThat(account.getRoles()).extracting(Role::getRoleCode)
				.containsExactlyInAnyOrder("WORKFLOW_USER", "FACULTY_TEACHER");
		assertThat(result.roleCodes()).containsExactly("FACULTY_TEACHER");
		verify(sources).deleteByUserIdAndSourceType("user-1", "POSITION_MAPPING");
		verify(sources).save(any(UserRoleAssignmentSource.class));
	}

	private Role role(String id, String code) {
		Role role = new Role();
		role.setId(id);
		role.setRoleCode(code);
		role.setStatus(1);
		return role;
	}

	private UserRoleAssignmentSource source(String roleId, String type) {
		UserRoleAssignmentSource source = new UserRoleAssignmentSource();
		source.setRoleId(roleId);
		source.setSourceType(type);
		return source;
	}
}
