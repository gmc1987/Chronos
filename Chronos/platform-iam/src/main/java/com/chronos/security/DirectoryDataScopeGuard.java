package com.chronos.security;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.service.iService.IDataScopeService;

/** Enforces organization scope on the server; UI filters are never trusted. */
@Component
public class DirectoryDataScopeGuard {
	private final IDataScopeService dataScopes;
	private final IEmployeeAssignmentRepository assignments;

	public DirectoryDataScopeGuard(IDataScopeService dataScopes,
			IEmployeeAssignmentRepository assignments) {
		this.dataScopes = dataScopes;
		this.assignments = assignments;
	}

	public boolean fullAccess(Authentication authentication) {
		return context(authentication).fullAccess();
	}

	public void requireFullAccess(Authentication authentication) {
		if (!fullAccess(authentication))
			throw new AccessDeniedException("仅允许全局数据管理员执行该操作");
	}

	/** Null means unrestricted; otherwise the returned set is the complete allow-list. */
	public Set<String> visibleOrganizationIds(Authentication authentication) {
		var context = context(authentication);
		if (context.fullAccess()) return null;
		Set<String> result = new LinkedHashSet<>(context.organizationIds());
		if (context.employeeId() != null) {
			assignments.findCurrentAssignments(context.employeeId(), LocalDate.now()).stream()
					.map(value -> value.getOrganizationId()).forEach(result::add);
		}
		return Set.copyOf(result);
	}

	public void requireOrganization(Authentication authentication, String organizationId) {
		if (organizationId == null || organizationId.isBlank())
			throw new IllegalArgumentException("请选择机构");
		Set<String> allowed = visibleOrganizationIds(authentication);
		if (allowed != null && !allowed.contains(organizationId))
			throw new AccessDeniedException("无权访问该机构数据");
	}

	public void requireEmployee(Authentication authentication, String employeeId) {
		var context = context(authentication);
		if (context.fullAccess() || context.employeeIds().contains(employeeId)) return;
		Set<String> allowed = visibleOrganizationIds(authentication);
		boolean visible = assignments.findCurrentAssignments(employeeId, LocalDate.now()).stream()
				.anyMatch(value -> allowed != null && allowed.contains(value.getOrganizationId()));
		if (!visible) throw new AccessDeniedException("无权访问该教职工数据");
	}

	private com.chronos.model.vo.DataScopeContext context(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated())
			throw new AccessDeniedException("用户未登录");
		return dataScopes.resolve(authentication.getName());
	}
}
