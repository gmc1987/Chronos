package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IUserRoleAssignmentSourceRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.UserRoleAssignmentSource;
import com.chronos.service.impl.UserRoleAssignmentService;

class UserRoleAssignmentServiceTest {
	@Test
	void shouldCreateBaseSourceWithoutRemovingBusinessRole() {
		Fixture fixture = fixture();
		Role baseRole = role("base-role", UserRoleAssignmentService.BASE_ROLE_CODE);
		Role businessRole = role("business-role", "FACULTY_TEACHER");
		fixture.account().setRoles(new HashSet<>(Set.of(businessRole)));
		when(fixture.roles().findByRoleCode(UserRoleAssignmentService.BASE_ROLE_CODE))
				.thenReturn(baseRole);

		fixture.service().ensureBaseAccountRole(fixture.account());

		assertThat(fixture.account().getRoles()).containsExactlyInAnyOrder(baseRole, businessRole);
		verify(fixture.sources()).save(any(UserRoleAssignmentSource.class));
	}

	@Test
	void shouldNotCreateDuplicateBaseSource() {
		Fixture fixture = fixture();
		Role baseRole = role("base-role", UserRoleAssignmentService.BASE_ROLE_CODE);
		when(fixture.roles().findByRoleCode(UserRoleAssignmentService.BASE_ROLE_CODE))
				.thenReturn(baseRole);
		when(fixture.sources().existsByUserIdAndRoleIdAndSourceTypeAndSourceKey(
				"user-1", "base-role", UserRoleAssignmentService.BASE_ACCOUNT,
				UserRoleAssignmentService.BASE_ROLE_CODE)).thenReturn(true);

		fixture.service().ensureBaseAccountRole(fixture.account());

		verify(fixture.sources(), never()).save(any(UserRoleAssignmentSource.class));
		assertThat(fixture.account().getRoles()).containsExactly(baseRole);
	}

	@Test
	void shouldReplaceOnlyManualSourcesAndKeepBaseAndAgentGrants() {
		Fixture fixture = fixture();
		Role requestedManual = role("manual-new", "WORKFLOW_REVIEWER");
		Role baseRole = role("base-role", UserRoleAssignmentService.BASE_ROLE_CODE);
		Role agentRole = role("agent-role", "FACULTY_TEACHER");
		UserRoleAssignmentSource oldManual = source("manual-old", UserRoleAssignmentService.MANUAL, "MANUAL");
		when(fixture.roles().findAllById(any())).thenReturn(
				List.of(requestedManual), List.of(baseRole, agentRole, requestedManual));
		when(fixture.sources().findByUserIdAndSourceType("user-1", UserRoleAssignmentService.MANUAL))
				.thenReturn(List.of(oldManual));
		when(fixture.sources().findByUserId("user-1")).thenReturn(List.of(
				source("base-role", UserRoleAssignmentService.BASE_ACCOUNT, "ROLE_PLATFORM_USER"),
				source("agent-role", UserRoleAssignmentService.AGENT_GRANT, "proposal-1"),
				source("manual-new", UserRoleAssignmentService.MANUAL, "MANUAL")));
		fixture.service().replaceManualRoles(fixture.account(), Set.of("manual-new"));

		verify(fixture.sources()).deleteAll(List.of(oldManual));
		verify(fixture.sources()).save(any(UserRoleAssignmentSource.class));
		assertThat(fixture.account().getRoles())
				.containsExactlyInAnyOrder(baseRole, agentRole, requestedManual);
	}

	@Test
	void shouldExposeOnlyManualRoleIdsForEditing() {
		Fixture fixture = fixture();
		when(fixture.sources().findByUserIdAndSourceType("user-1", UserRoleAssignmentService.MANUAL))
				.thenReturn(List.of(
						source("manual-1", UserRoleAssignmentService.MANUAL, "MANUAL"),
						source("manual-2", UserRoleAssignmentService.MANUAL, "TEACHER_PROVISIONING")));

		assertThat(fixture.service().manualRoleIds("user-1"))
				.containsExactly("manual-1", "manual-2");
	}

	@Test
	void shouldReplaceOnlyOneAgentGrantScopeAndRecalculateEffectiveRoles() {
		Fixture fixture = fixture();
		Role requested = role("agent-new", "EXAM_REVIEWER");
		Role retained = role("agent-retained", "GRADE_REVIEWER");
		UserRoleAssignmentSource old = source("agent-old", UserRoleAssignmentService.AGENT_GRANT, "OPERATION_AGENT");
		when(fixture.roles().findAllById(any())).thenReturn(
				List.of(requested), List.of(requested, retained));
		when(fixture.sources().findByUserIdAndSourceType("user-1", UserRoleAssignmentService.AGENT_GRANT))
				.thenReturn(List.of(old,
						source("agent-retained", UserRoleAssignmentService.AGENT_GRANT, "OTHER_AGENT")));
		when(fixture.sources().findByUserId("user-1")).thenReturn(List.of(
				source("agent-new", UserRoleAssignmentService.AGENT_GRANT, "OPERATION_AGENT"),
				source("agent-retained", UserRoleAssignmentService.AGENT_GRANT, "OTHER_AGENT")));

		fixture.service().replaceAgentRoles(fixture.account(), Set.of("agent-new"), "OPERATION_AGENT");

		verify(fixture.sources()).deleteAll(List.of(old));
		assertThat(fixture.account().getRoles()).containsExactlyInAnyOrder(requested, retained);
		assertThat(fixture.account().getTokenVersion()).isEqualTo(1);
	}

	private Fixture fixture() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		IRoleRepository roles = mock(IRoleRepository.class);
		IUserRoleAssignmentSourceRepository sources = mock(IUserRoleAssignmentSourceRepository.class);
		when(users.save(any(AdminUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AdminUser account = new AdminUser();
		account.setId("user-1");
		account.setRoles(new HashSet<>());
		return new Fixture(new UserRoleAssignmentService(users, roles, sources), roles, sources, account);
	}

	private Role role(String id, String code) {
		Role role = new Role();
		role.setId(id);
		role.setRoleCode(code);
		role.setStatus(1);
		return role;
	}

	private UserRoleAssignmentSource source(String roleId, String sourceType, String sourceKey) {
		UserRoleAssignmentSource source = new UserRoleAssignmentSource();
		source.setUserId("user-1");
		source.setRoleId(roleId);
		source.setSourceType(sourceType);
		source.setSourceKey(sourceKey);
		return source;
	}

	private record Fixture(UserRoleAssignmentService service, IRoleRepository roles,
			IUserRoleAssignmentSourceRepository sources, AdminUser account) {
	}
}
