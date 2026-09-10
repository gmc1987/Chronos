package com.chronos.service.impl;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IRoleDataScopeRepository;
import com.chronos.model.pojo.BaseEntity;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.RoleDataScope;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.service.iService.IDataScopeService;

@Service
@Transactional(readOnly = true)
public class DataScopeServiceImpl implements IDataScopeService {
	private final IAdminUserRepository users;
	private final IEmployeeAssignmentRepository assignments;
	private final IOrganizationUnitRepository units;
	private final IRoleDataScopeRepository scopes;

	public DataScopeServiceImpl(IAdminUserRepository users, IEmployeeAssignmentRepository assignments,
			IOrganizationUnitRepository units, IRoleDataScopeRepository scopes) {
		this.users = users;
		this.assignments = assignments;
		this.units = units;
		this.scopes = scopes;
	}

	public DataScopeContext resolve(String username) {
		var user = users.findByUsername(username);
		if (user == null)
			throw new IllegalArgumentException("user not found");
		String employeeId = user.getEmployeeId();
		Set<String> orgIds = new HashSet<>(), unitIds = new HashSet<>(), employeeIds = new HashSet<>();
		if (employeeId != null)
			employeeIds.add(employeeId);
		var roleIds = user.getRoles().stream().map(BaseEntity::getId).toList();
		List<RoleDataScope> configured = roleIds.isEmpty() ? List.of() : scopes.findByRoleIdIn(roleIds);
		if (configured.stream().anyMatch(s -> "ALL".equals(s.getScopeType())))
			return new DataScopeContext(
					true,
					employeeId,
					Set.of(),
					Set.of(),
					Set.of(),
					Set.of("ALL"),
					Map.of());
		var primary = employeeId == null ? java.util.Optional.<com.chronos.model.pojo.EmployeeAssignment>empty()
				: assignments.findCurrentPrimaryAssignment(employeeId, java.time.LocalDate.now());
		if (configured.isEmpty() || configured.stream().anyMatch(s -> "SELF".equals(s.getScopeType())))
			employeeIds.add(employeeId);
		Set<String> scopeTypes = new HashSet<>();
		Map<String, Set<String>> resourceIds = new HashMap<>();
		for (RoleDataScope scope : configured) {
			scopeTypes.add(scope.getScopeType());
			if (scope.getOrganizationId() != null)
				orgIds.add(scope.getOrganizationId());
			if (scope.getOrganizationUnitId() != null)
				unitIds.add(scope.getOrganizationUnitId());
			if (scope.getEmployeeId() != null)
				employeeIds.add(scope.getEmployeeId());
			if (scope.getResourceType() != null && scope.getResourceId() != null) {
				resourceIds.computeIfAbsent(scope.getResourceType(), key -> new HashSet<>())
						.add(scope.getResourceId());
			}
			if ("DEPARTMENT".equals(scope.getScopeType()))
				primary.ifPresent(a -> {
					orgIds.add(a.getOrganizationId());
					unitIds.add(a.getOrganizationUnitId());
				});
			if ("DEPARTMENT_AND_CHILDREN".equals(scope.getScopeType()))
				primary.ifPresent(a -> {
					orgIds.add(a.getOrganizationId());
					addDescendants(a.getOrganizationId(), a.getOrganizationUnitId(), unitIds);
				});
		}
		employeeIds.remove(null);
		if (configured.isEmpty()) {
			scopeTypes.add("SELF");
		}
		Map<String, Set<String>> immutableResources = resourceIds.entrySet().stream()
				.collect(java.util.stream.Collectors.toUnmodifiableMap(
						Map.Entry::getKey,
						entry -> Set.copyOf(entry.getValue())));
		return new DataScopeContext(
				false,
				employeeId,
				Set.copyOf(orgIds),
				Set.copyOf(unitIds),
				Set.copyOf(employeeIds),
				Set.copyOf(scopeTypes),
				immutableResources);
	}

	private void addDescendants(String orgId, String rootId, Set<String> result) {
		result.add(rootId);
		List<OrganizationUnit> all = units.findByOrgIdAndStatusOrderBySortOrderAsc(orgId, 1);
		boolean changed;
		do {
			changed = false;
			for (OrganizationUnit unit : all) {
				var parent = unit.getParentOrganizationUnit();
				if (parent != null && result.contains(parent.getId()) && result.add(unit.getId()))
					changed = true;
			}
		} while (changed);
	}
}
