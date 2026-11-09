package com.chronos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.service.iService.IDataScopeService;

class DirectoryDataScopeGuardTest {

	@Test
	void nonAdminCanOnlyUseOrganizationsInRoleOrCurrentAssignmentScope() {
		IDataScopeService dataScopes = mock(IDataScopeService.class);
		IEmployeeAssignmentRepository assignments = mock(IEmployeeAssignmentRepository.class);
		DirectoryDataScopeGuard guard = new DirectoryDataScopeGuard(dataScopes, assignments);
		var authentication = new UsernamePasswordAuthenticationToken("school-manager", "", List.of());
		when(dataScopes.resolve("school-manager")).thenReturn(new DataScopeContext(
				false, "employee-1", Set.of("org-role"), Set.of(), Set.of("employee-1"),
				Set.of("ORGANIZATION"), Map.of()));
		EmployeeAssignment current = new EmployeeAssignment();
		current.setOrganizationId("org-assignment");
		when(assignments.findCurrentAssignments(
				org.mockito.ArgumentMatchers.eq("employee-1"), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of(current));

		assertThat(guard.visibleOrganizationIds(authentication))
				.containsExactlyInAnyOrder("org-role", "org-assignment");
		guard.requireOrganization(authentication, "org-role");
		guard.requireOrganization(authentication, "org-assignment");
		assertThatThrownBy(() -> guard.requireOrganization(authentication, "org-other"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("无权访问");
	}

	@Test
	void allScopeKeepsAdminUnrestricted() {
		IDataScopeService dataScopes = mock(IDataScopeService.class);
		DirectoryDataScopeGuard guard = new DirectoryDataScopeGuard(
				dataScopes, mock(IEmployeeAssignmentRepository.class));
		var authentication = new UsernamePasswordAuthenticationToken("admin", "", List.of());
		when(dataScopes.resolve("admin")).thenReturn(new DataScopeContext(
				true, null, Set.of(), Set.of(), Set.of(), Set.of("ALL"), Map.of()));

		assertThat(guard.visibleOrganizationIds(authentication)).isNull();
		guard.requireOrganization(authentication, "any-organization");
		assertThatThrownBy(() -> guard.resolveOrganization(authentication, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("请选择机构");
	}

	@Test
	void resolvesOnlyOrganizationButRejectsMissingOrAmbiguousScope() {
		IDataScopeService dataScopes = mock(IDataScopeService.class);
		IEmployeeAssignmentRepository assignments = mock(IEmployeeAssignmentRepository.class);
		DirectoryDataScopeGuard guard = new DirectoryDataScopeGuard(dataScopes, assignments);
		var authentication = new UsernamePasswordAuthenticationToken("manager", "", List.of());
		when(assignments.findCurrentAssignments(
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of());

		when(dataScopes.resolve("manager")).thenReturn(new DataScopeContext(
				false, null, Set.of("org-only"), Set.of(), Set.of(), Set.of("ORGANIZATION"), Map.of()));
		assertThat(guard.resolveOrganization(authentication, null)).isEqualTo("org-only");
		assertThat(guard.resolveQueryOrganizations(authentication, null)).containsExactly("org-only");

		when(dataScopes.resolve("manager")).thenReturn(new DataScopeContext(
				false, null, Set.of("org-a", "org-b"), Set.of(), Set.of(), Set.of("ORGANIZATION"), Map.of()));
		assertThatThrownBy(() -> guard.resolveOrganization(authentication, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("请选择机构");

		when(dataScopes.resolve("manager")).thenReturn(new DataScopeContext(
				false, null, Set.of(), Set.of(), Set.of(), Set.of("ORGANIZATION"), Map.of()));
		assertThatThrownBy(() -> guard.resolveOrganization(authentication, null))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("当前账号没有可访问的机构");
	}
}
