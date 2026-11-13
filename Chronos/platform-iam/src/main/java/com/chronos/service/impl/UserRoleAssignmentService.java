package com.chronos.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IUserRoleAssignmentSourceRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.UserRoleAssignmentSource;

/**
 * 统一维护角色授权来源和实际用户角色关系。
 *
 * <p>来源记录是授权事实，{@code t_user_role} 只是所有有效来源的并集。任何单一
 * 来源的变更都不能直接覆盖其他来源授予的角色。</p>
 */
@Service
public class UserRoleAssignmentService {
	public static final String BASE_ACCOUNT = "BASE_ACCOUNT";
	public static final String MANUAL = "MANUAL";
	public static final String AGENT_GRANT = "AGENT_GRANT";
	public static final String BASE_ROLE_CODE = "ROLE_PLATFORM_USER";
	private static final String BASE_SOURCE_KEY = BASE_ROLE_CODE;
	private static final String MANUAL_SOURCE_KEY = MANUAL;

	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final IUserRoleAssignmentSourceRepository sources;

	public UserRoleAssignmentService(IAdminUserRepository users,
			IRoleRepository roles,
			IUserRoleAssignmentSourceRepository sources) {
		this.users = users;
		this.roles = roles;
		this.sources = sources;
	}

	/** 确保教职工账号始终具有可登录的最小基础角色及其来源。 */
	@Transactional
	public Role ensureBaseAccountRole(AdminUser account) {
		Role baseRole = roles.findByRoleCode(BASE_ROLE_CODE);
		if (baseRole == null || !Integer.valueOf(1).equals(baseRole.getStatus())) {
			throw new IllegalStateException("基础角色 ROLE_PLATFORM_USER 不存在或未启用");
		}
		ensureSource(account.getId(), baseRole.getId(), BASE_ACCOUNT, BASE_SOURCE_KEY);
		Set<Role> effectiveRoles = new HashSet<>(safeRoles(account));
		effectiveRoles.add(baseRole);
		account.setRoles(effectiveRoles);
		users.save(account);
		return baseRole;
	}

	/**
	 * 替换用户管理页面维护的人工来源，并重新计算全部来源的角色并集。
	 */
	@Transactional
	public void replaceManualRoles(AdminUser account, Collection<String> requestedRoleIds) {
		Set<String> requested = requestedRoleIds == null
				? Set.of()
				: requestedRoleIds.stream().filter(value -> value != null && !value.isBlank())
						.collect(Collectors.toCollection(LinkedHashSet::new));
		List<Role> requestedRoles = roles.findAllById(requested);
		if (requestedRoles.size() != requested.size()) {
			throw new IllegalArgumentException("包含不存在的角色，无法保存人工授权");
		}
		if (requestedRoles.stream().anyMatch(role -> !Integer.valueOf(1).equals(role.getStatus()))) {
			throw new IllegalArgumentException("停用角色不能授予用户");
		}

		List<UserRoleAssignmentSource> currentManual = sources.findByUserIdAndSourceType(
				account.getId(), MANUAL);
		List<UserRoleAssignmentSource> removed = currentManual.stream()
				.filter(source -> !requested.contains(source.getRoleId())).toList();
		if (!removed.isEmpty()) {
			sources.deleteAll(removed);
			sources.flush();
		}
		Set<String> existingManualRoleIds = currentManual.stream()
				.filter(source -> requested.contains(source.getRoleId()))
				.map(UserRoleAssignmentSource::getRoleId).collect(Collectors.toSet());
		for (String roleId : requested) {
			if (!existingManualRoleIds.contains(roleId)) {
				ensureSource(account.getId(), roleId, MANUAL, MANUAL_SOURCE_KEY);
			}
		}
		reconcileEffectiveRoles(account);
	}

	/** 用于明确的业务开通流程记录一项人工业务角色来源。 */
	@Transactional
	public void ensureManualRole(AdminUser account, Role role, String sourceKey) {
		if (role == null || !Integer.valueOf(1).equals(role.getStatus())) return;
		ensureSource(account.getId(), role.getId(), MANUAL,
				sourceKey == null || sourceKey.isBlank() ? MANUAL_SOURCE_KEY : sourceKey);
		reconcileEffectiveRoles(account);
	}

	@Transactional
	public void removeManualRoleSource(AdminUser account, String sourceKey) {
		var matched = sources.findByUserIdAndSourceType(account.getId(), MANUAL).stream()
				.filter(source -> sourceKey.equals(source.getSourceKey())).toList();
		if (matched.isEmpty()) return;
		sources.deleteAll(matched);
		sources.flush();
		reconcileEffectiveRoles(account);
	}

	@Transactional(readOnly = true)
	public Set<String> manualRoleIds(String userId) {
		return sources.findByUserIdAndSourceType(userId, MANUAL).stream()
				.map(UserRoleAssignmentSource::getRoleId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Replace the roles granted by one approved agent operation without touching
	 * base-account or manually maintained role sources. The source key is the
	 * agent run/plan identifier and therefore also acts as the revocation scope.
	 */
	@Transactional
	public void replaceAgentRoles(AdminUser account, Collection<String> requestedRoleIds, String sourceKey) {
		if (account == null || account.getId() == null) {
			throw new IllegalArgumentException("用户账号不存在");
		}
		if (sourceKey == null || sourceKey.isBlank()) {
			throw new IllegalArgumentException("智能授权来源不能为空");
		}
		Set<String> requested = requestedRoleIds == null
				? Set.of()
				: requestedRoleIds.stream().filter(value -> value != null && !value.isBlank())
						.collect(Collectors.toCollection(LinkedHashSet::new));
		List<Role> requestedRoles = roles.findAllById(requested);
		if (requestedRoles.size() != requested.size()) {
			throw new IllegalArgumentException("智能授权包含不存在的角色");
		}
		if (requestedRoles.stream().anyMatch(role -> !Integer.valueOf(1).equals(role.getStatus()))) {
			throw new IllegalArgumentException("停用角色不能通过智能体授予用户");
		}

		List<UserRoleAssignmentSource> scoped = sources.findByUserIdAndSourceType(account.getId(), AGENT_GRANT)
				.stream().filter(source -> sourceKey.equals(source.getSourceKey())).toList();
		List<UserRoleAssignmentSource> removed = scoped.stream()
				.filter(source -> !requested.contains(source.getRoleId())).toList();
		if (!removed.isEmpty()) {
			sources.deleteAll(removed);
			sources.flush();
		}
		Set<String> existing = scoped.stream().filter(source -> requested.contains(source.getRoleId()))
				.map(UserRoleAssignmentSource::getRoleId).collect(Collectors.toSet());
		for (String roleId : requested) {
			if (!existing.contains(roleId)) ensureSource(account.getId(), roleId, AGENT_GRANT, sourceKey);
		}
		reconcileEffectiveRoles(account);
		account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
		users.save(account);
	}

	@Transactional
	public void reconcileEffectiveRoles(AdminUser account) {
		Set<String> roleIds = sources.findByUserId(account.getId()).stream()
				.map(UserRoleAssignmentSource::getRoleId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		account.setRoles(new HashSet<>(roles.findAllById(roleIds)));
		users.save(account);
	}

	private void ensureSource(String userId, String roleId, String sourceType, String sourceKey) {
		if (sources.existsByUserIdAndRoleIdAndSourceTypeAndSourceKey(
				userId, roleId, sourceType, sourceKey)) return;
		UserRoleAssignmentSource source = new UserRoleAssignmentSource();
		source.setUserId(userId);
		source.setRoleId(roleId);
		source.setSourceType(sourceType);
		source.setSourceKey(sourceKey);
		sources.save(source);
	}

	private Set<Role> safeRoles(AdminUser account) {
		return account.getRoles() == null ? Set.of() : account.getRoles();
	}
}
