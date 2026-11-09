package com.chronos.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.Role;
import com.chronos.service.iService.IRefreshTokenService;

@Service
public class StaffAccountProvisioningService {
	/**
	 * Candidate imports are always rolled back. A fixed non-secret marker keeps the
	 * transient entity structurally complete without spending BCrypt work for every
	 * proposed account.
	 */
	private static final String DRY_RUN_PASSWORD_MARKER = "{dry-run}not-persisted";
	private final IAdminUserRepository users;
	private final UserRoleAssignmentService roleAssignments;
	private final PasswordEncoder passwordEncoder;
	private final IRefreshTokenService refreshTokens;
	private final SecureRandom random = new SecureRandom();

	public StaffAccountProvisioningService(IAdminUserRepository users,
			UserRoleAssignmentService roleAssignments,
			PasswordEncoder passwordEncoder,
			IRefreshTokenService refreshTokens) {
		this.users = users;
		this.roleAssignments = roleAssignments;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
	}

	@Transactional
	public AccountSyncResult sync(Employee employee, boolean disableInactiveAccounts, boolean dryRun) {
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
			if (dryRun) {
				account.setPassword(DRY_RUN_PASSWORD_MARKER);
			} else {
				temporaryPassword = generateTemporaryPassword();
				account.setPassword(passwordEncoder.encode(temporaryPassword));
			}
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
		Role baseRole = roleAssignments.ensureBaseAccountRole(account);
		roleCodes.add(baseRole.getRoleCode());
		if (!active && disableInactiveAccounts) {
			account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
			users.save(account);
			if (!dryRun) refreshTokens.revokeAll(account.getUsername());
		}
		return new AccountSyncResult(employee.getEmployeeCode(), employee.getEmployeeName(),
				account.getUsername(), created, dryRun ? null : temporaryPassword, roleCodes, warnings);
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
