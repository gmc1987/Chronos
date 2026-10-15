package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IRoleDataScopeRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.RoleDataScope;
import com.chronos.service.impl.DataScopeServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataScopeServiceOrganizationStatusTest {
	@Test
	void disabledSchoolImmediatelyRemovesItsCampusFromTheSameUsersScope() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		IEmployeeAssignmentRepository assignments = mock(IEmployeeAssignmentRepository.class);
		IOrganizationUnitRepository units = mock(IOrganizationUnitRepository.class);
		IOrganizationRepository organizations = mock(IOrganizationRepository.class);
		IRoleDataScopeRepository scopes = mock(IRoleDataScopeRepository.class);
		DataScopeServiceImpl service = new DataScopeServiceImpl(users, assignments, units, organizations, scopes);

		Role role = new Role();
		role.setId("school-manager-role");
		AdminUser user = new AdminUser();
		user.setRoles(Set.of(role));
		when(users.findByUsername("manager")).thenReturn(user);
		RoleDataScope scope = new RoleDataScope();
		scope.setRoleId(role.getId());
		scope.setScopeType("ORGANIZATION");
		scope.setOrganizationId("campus");
		when(scopes.findByRoleIdIn(List.of(role.getId()))).thenReturn(List.of(scope));

		Organization school = new Organization();
		school.setId("school");
		school.setStatus(1);
		Organization campus = new Organization();
		campus.setId("campus");
		campus.setStatus(1);
		campus.setParentOrgId(school);
		when(organizations.findById("campus")).thenReturn(Optional.of(campus));

		assertThat(service.resolve("manager").organizationIds()).containsExactly("campus");
		school.setStatus(0);
		assertThat(service.resolve("manager").organizationIds()).isEmpty();
		school.setStatus(1);
		assertThat(service.resolve("manager").organizationIds()).containsExactly("campus");
		when(organizations.findById("campus")).thenReturn(Optional.empty());
		assertThat(service.resolve("manager").organizationIds()).isEmpty();
	}

	@Test
	void disabledDepartmentAncestorRemovesAChildDepartmentScope() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		IEmployeeAssignmentRepository assignments = mock(IEmployeeAssignmentRepository.class);
		IOrganizationUnitRepository units = mock(IOrganizationUnitRepository.class);
		IOrganizationRepository organizations = mock(IOrganizationRepository.class);
		IRoleDataScopeRepository scopes = mock(IRoleDataScopeRepository.class);
		DataScopeServiceImpl service = new DataScopeServiceImpl(users, assignments, units, organizations, scopes);

		Role role = new Role();
		role.setId("department-role");
		AdminUser user = new AdminUser();
		user.setRoles(Set.of(role));
		when(users.findByUsername("manager")).thenReturn(user);
		RoleDataScope scope = new RoleDataScope();
		scope.setScopeType("CUSTOM_DEPARTMENT");
		scope.setOrganizationUnitId("child-unit");
		when(scopes.findByRoleIdIn(List.of(role.getId()))).thenReturn(List.of(scope));

		Organization organization = new Organization();
		organization.setId("school");
		organization.setStatus(1);
		when(organizations.findById("school")).thenReturn(Optional.of(organization));
		OrganizationUnit parent = new OrganizationUnit();
		parent.setId("parent-unit");
		parent.setOrgId("school");
		parent.setStatus(1);
		OrganizationUnit child = new OrganizationUnit();
		child.setId("child-unit");
		child.setOrgId("school");
		child.setStatus(1);
		child.setParentOrganizationUnit(parent);
		when(units.findById("child-unit")).thenReturn(Optional.of(child));

		assertThat(service.resolve("manager").organizationUnitIds()).containsExactly("child-unit");
		parent.setStatus(0);
		assertThat(service.resolve("manager").organizationUnitIds()).isEmpty();
		parent.setStatus(1);
		when(units.findById("child-unit")).thenReturn(Optional.empty());
		assertThat(service.resolve("manager").organizationUnitIds()).isEmpty();
	}
}
