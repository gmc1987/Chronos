package com.chronos.education.scheduling.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;

/** 在新生报到建档时同步创建平台账号和教育身份绑定，供 App 与微信小程序统一使用。 */
@Service
public class StudentAccountProvisioningService {
	private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final EducationUserBindingRepository bindings;
	private final PasswordEncoder passwords;
	private final SecureRandom random = new SecureRandom();

	public StudentAccountProvisioningService(IAdminUserRepository users, IRoleRepository roles,
			EducationUserBindingRepository bindings, PasswordEncoder passwords) {
		this.users = users;
		this.roles = roles;
		this.bindings = bindings;
		this.passwords = passwords;
	}

	@Transactional
	public String provision(StudentProfile student, String organizationId) {
		if (student == null || student.getId() == null || student.getStudentNo() == null
				|| student.getStudentNo().isBlank()) throw new IllegalArgumentException("学生档案和学号不能为空");
		EducationUserBinding existingBinding = bindings
				.findByProfileTypeAndProfileIdAndStatus("STUDENT", student.getId(), "ACTIVE").orElse(null);
		if (existingBinding != null) return existingBinding.getUsername();

		String username = student.getStudentNo().trim().toLowerCase();
		AdminUser account = users.findByUsername(username);
		if (account == null) {
			Role role = roles.findByRoleCode("EDU_STUDENT");
			if (role == null) role = roles.findByRoleCode("ROLE_PLATFORM_USER");
			if (role == null) throw new IllegalStateException("系统尚未配置学生门户角色");
			account = new AdminUser();
			account.setUsername(username);
			account.setPassword(passwords.encode(randomPassword()));
			account.setDisplayName(student.getStudentName());
			account.setPhone(student.getPhone());
			account.setOrganizationId(organizationId);
			account.setAccountType("STUDENT");
			account.setAccountLocked(false);
			account.setFailedLoginAttempts(0);
			account.setMustChangePassword(true);
			account.setStatus(1);
			account.setCreateTime(LocalDateTime.now());
			account.setRoles(new HashSet<>(Set.of(role)));
			users.save(account);
		} else if (bindings.findByUsername(username).isPresent()) {
			throw new IllegalStateException("学号对应账号已绑定其他教育档案");
		}

		EducationUserBinding binding = new EducationUserBinding();
		binding.setUsername(username);
		binding.setProfileType("STUDENT");
		binding.setProfileId(student.getId());
		binding.setStatus("ACTIVE");
		binding.setVerifiedAt(LocalDateTime.now());
		bindings.save(binding);
		return username;
	}

	private String randomPassword() {
		StringBuilder value = new StringBuilder(20);
		value.append('A').append('a').append('2');
		while (value.length() < 20) value.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
		return value.toString();
	}
}
