package com.chronos.service.impl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IPositionRoleMappingRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IUserRoleAssignmentSourceRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.PositionRoleMapping;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.UserRoleAssignmentSource;
import com.chronos.service.iService.IRefreshTokenService;

@Service
public class StaffAccountProvisioningService {
	private static final String POSITION_MAPPING = "POSITION_MAPPING";
	private final IAdminUserRepository users;
	private final IEmployeeAssignmentRepository assignments;
	private final IPositionRoleMappingRepository mappings;
	private final IUserRoleAssignmentSourceRepository roleSources;
	private final IRoleRepository roles;
	private final PasswordEncoder passwordEncoder;
	private final IRefreshTokenService refreshTokens;
	private final SecureRandom random = new SecureRandom();

	public StaffAccountProvisioningService(IAdminUserRepository users,
			IEmployeeAssignmentRepository assignments,
			IPositionRoleMappingRepository mappings,
			IUserRoleAssignmentSourceRepository roleSources,
			IRoleRepository roles,
			PasswordEncoder passwordEncoder,
			IRefreshTokenService refreshTokens) {
		this.users = users;
		this.assignments = assignments;
		this.mappings = mappings;
		this.roleSources = roleSources;
		this.roles = roles;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
	}

	@Transactional
	public AccountSyncResult sync(Employee employee, boolean syncRoles,
			boolean disableInactiveAccounts, boolean dryRun) {
		AdminUser account = users.findByEmployeeId(employee.getId()).orElse(null);
		boolean created = account == null;
		String temporaryPassword = null;
		if (created) {
			AdminUser conflicting = users.findByUsername(employee.getEmployeeCode());
			if (conflicting != null) {
				throw new IllegalArgumentException("登录账号已被其他人员占用：" + employee.getEmployeeCode());
			}
			account = new AdminUser();
			account.setUsername(employee.getEmployeeCode());
			account.setEmployeeId(employee.getId());
			account.setAccountType("STAFF");
			account.setAccountLocked(false);
			account.setFailedLoginAttempts(0);
			account.setMustChangePassword(true);
			account.setTokenVersion(0);
			account.setCreateTime(LocalDateTime.now());
			temporaryPassword = generateTemporaryPassword();
			account.setPassword(passwordEncoder.encode(temporaryPassword));
		}
		account.setDisplayName(employee.getEmployeeName());
		account.setPhone(employee.getPhone());
		account.setEmail(employee.getEmail());
		boolean active = "ACTIVE".equals(employee.getEmploymentStatus());
		if (!active && disableInactiveAccounts) {
			account.setStatus(0);
			account.setAccountLocked(true);
		} else if (created || active) {
			account.setStatus(1);
			account.setAccountLocked(false);
		}
		account = users.save(account);

		List<String> warnings = new ArrayList<>();
		Set<String> roleCodes = new LinkedHashSet<>();
		if (syncRoles) {
			roleCodes.addAll(syncPositionRoles(account));
			if (roleCodes.isEmpty()) warnings.add("未配置有效的岗位角色映射");
		}
		if (!active && disableInactiveAccounts) {
			account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
			users.save(account);
			if (!dryRun) refreshTokens.revokeAll(account.getUsername());
		}
		return new AccountSyncResult(employee.getEmployeeCode(), employee.getEmployeeName(),
				account.getUsername(), created, dryRun ? null : temporaryPassword, roleCodes, warnings);
	}

	private Set<String> syncPositionRoles(AdminUser account) {
		Set<String> previousManagedRoleIds = roleSources.findByUserIdAndSourceType(account.getId(), POSITION_MAPPING)
				.stream().map(UserRoleAssignmentSource::getRoleId).collect(java.util.stream.Collectors.toSet());
		Set<String> manualRoleIds = roleSources.findByUserIdAndSourceType(account.getId(), "MANUAL")
				.stream().map(UserRoleAssignmentSource::getRoleId).collect(java.util.stream.Collectors.toSet());
		Set<Role> finalRoles = new HashSet<>(account.getRoles());
		finalRoles.removeIf(role -> previousManagedRoleIds.contains(role.getId())
				&& !manualRoleIds.contains(role.getId()));
		roleSources.deleteByUserIdAndSourceType(account.getId(), POSITION_MAPPING);

		Set<String> roleCodes = new LinkedHashSet<>();
		Set<String> appliedMappingIds = new HashSet<>();
		for (var assignment : assignments.findCurrentAssignments(account.getEmployeeId(), LocalDate.now())) {
			for (PositionRoleMapping mapping : mappings.findByPositionIdAndStatusOrderByPriorityDesc(
					assignment.getPositionId(), 1)) {
				if (mapping.getOrganizationId() != null
						&& !mapping.getOrganizationId().equals(assignment.getOrganizationId())) continue;
				if (mapping.getOrganizationUnitId() != null
						&& !mapping.getOrganizationUnitId().equals(assignment.getOrganizationUnitId())) continue;
				if (Boolean.TRUE.equals(mapping.getPrimaryAssignmentOnly())
						&& !Boolean.TRUE.equals(assignment.getPrimaryAssignment())) continue;
				if (Boolean.TRUE.equals(mapping.getDepartmentLeaderOnly())
						&& !Boolean.TRUE.equals(assignment.getDepartmentLeader())) continue;
				Role role = roles.findById(mapping.getRoleId())
						.filter(value -> Integer.valueOf(1).equals(value.getStatus())).orElse(null);
				if (role == null) continue;
				finalRoles.add(role);
				roleCodes.add(role.getRoleCode());
				if (!appliedMappingIds.add(mapping.getId())) continue;
				UserRoleAssignmentSource source = new UserRoleAssignmentSource();
				source.setUserId(account.getId());
				source.setRoleId(role.getId());
				source.setSourceType(POSITION_MAPPING);
				source.setSourceKey(mapping.getId());
				roleSources.save(source);
			}
		}
		account.setRoles(finalRoles);
		users.save(account);
		return roleCodes;
	}

	private String generateTemporaryPassword() {
		String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
		String lower = "abcdefghijkmnopqrstuvwxyz";
		String digits = "23456789";
		String all = upper + lower + digits;
		StringBuilder value = new StringBuilder(12);
		value.append(upper.charAt(random.nextInt(upper.length())));
		value.append(lower.charAt(random.nextInt(lower.length())));
		value.append(digits.charAt(random.nextInt(digits.length())));
		while (value.length() < 12) value.append(all.charAt(random.nextInt(all.length())));
		return value.toString();
	}

	public record AccountSyncResult(String employeeCode, String employeeName, String username,
			boolean created, String temporaryPassword, Set<String> roleCodes, List<String> warnings) {}
}
