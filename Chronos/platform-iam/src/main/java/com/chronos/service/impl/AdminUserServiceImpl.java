package com.chronos.service.impl;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.commons.utils.BeanCopyUtil;
import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.model.vo.AdminUserVO;
import com.chronos.model.vo.RoleVO;
import com.chronos.service.iService.IAdminUserService;
import com.chronos.service.iService.IRefreshTokenService;

@Service("adminUserService")
public class AdminUserServiceImpl implements IAdminUserService {
	@Autowired
	private IAdminUserRepository adminUserRepository;
	@Autowired
	private IRoleRepository roleRepository;
	@Autowired
	private IEmployeeRepository employeeRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired private IRefreshTokenService refreshTokenService;

	public Page<AdminUser> pageUsers(AdminUserDTO dto, Pageable pageable) {
		return this.adminUserRepository.findAll(pageable);
	}

	public AdminUserVO getUserById(String id) {
		Optional<AdminUser> opt = this.adminUserRepository.findById(id);
		if (!opt.isPresent())
			return null;
		AdminUser u = opt.get();
		AdminUserVO vo = AdminUserVO.builder().build();
		BeanUtils.copyProperties(u, vo);

		if (u.getRoles() != null) {
			Set<Role> roles = u.getRoles();
			Set<RoleVO> roleVos = new HashSet<>();
			for (Role r : roles) {
				RoleVO rvo = RoleVO.builder().id(r.getId()).roleName(r.getRoleName()).roleCode(r.getRoleCode())
						.status(r.getStatus()).builtIn(r.getBuiltIn()).description(r.getDescription()).build();
				roleVos.add(rvo);
			}

			try {
				Field f = AdminUserVO.class.getDeclaredField("roles");
				f.setAccessible(true);
				f.set(vo, roleVos);
			} catch (Exception exception) {
			}
		}

		return vo;
	}

	public AdminUserVO getUserByEmployeeId(String employeeId) {
		return adminUserRepository.findByEmployeeId(employeeId).map(user -> getUserById(user.getId())).orElse(null);
	}

	@Transactional
	public void save(AdminUserDTO dto) {
		if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()) throw new IllegalArgumentException("username required");
		if (dto.getPassword() == null || dto.getPassword().isBlank()) throw new IllegalArgumentException("password required");
		ensureUsernameAvailable(dto.getUsername(), null);
		validateEmployeeBinding(dto.getEmployeeId(), null);
		validatePassword(dto.getPassword());
		AdminUser user = new AdminUser();
		BeanCopyUtil.copyNonNullProperties(dto, user);
		if (user.getCreateTime() == null) {
			user.setCreateTime(LocalDateTime.now());
		}

		if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
			user.setPassword(this.passwordEncoder.encode(dto.getPassword()));
			user.setPasswordChangedAt(LocalDateTime.now());
		}
		if (user.getAccountType() == null) user.setAccountType("STAFF");
		if (user.getAccountLocked() == null) user.setAccountLocked(false);
		if (user.getFailedLoginAttempts() == null) user.setFailedLoginAttempts(0);
		if (user.getMustChangePassword() == null) user.setMustChangePassword(false);

		if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
			Set<Role> roles = new HashSet<>(this.roleRepository.findAllById(dto.getRoleIds()));
			user.setRoles(roles);
		}
		this.adminUserRepository.save(user);
	}

	@Transactional
	public void update(AdminUserDTO dto) {
		if (dto == null || dto.getId() == null)
			throw new IllegalArgumentException("id required");
		Optional<AdminUser> opt = this.adminUserRepository.findById(dto.getId());
		if (!opt.isPresent())
			throw new IllegalArgumentException("user not found");
		AdminUser user = opt.get();
		ensureUsernameAvailable(dto.getUsername(), user.getId());
		validateEmployeeBinding(dto.getEmployeeId(), user.getId());
		if (dto.getUsername() != null)
			user.setUsername(dto.getUsername());
		if (dto.getEmail() != null)
			user.setEmail(dto.getEmail());
		if (dto.getDisplayName() != null)
			user.setDisplayName(dto.getDisplayName());
		if (dto.getPhone() != null)
			user.setPhone(dto.getPhone());
		if (dto.getAvatarUrl() != null)
			user.setAvatarUrl(dto.getAvatarUrl());
		if (dto.getOrganizationId() != null)
			user.setOrganizationId(dto.getOrganizationId());
		if (dto.getPositionName() != null)
			user.setPositionName(dto.getPositionName());
		if (dto.getEmployeeId() != null)
			user.setEmployeeId(dto.getEmployeeId());
		if (dto.getAccountType() != null)
			user.setAccountType(dto.getAccountType());
		if (dto.getAccountLocked() != null)
			user.setAccountLocked(dto.getAccountLocked());
		if (dto.getMustChangePassword() != null)
			user.setMustChangePassword(dto.getMustChangePassword());
		if (dto.getStatus() != null) {
			boolean disabling = dto.getStatus() == 0 && !Integer.valueOf(0).equals(user.getStatus());
			user.setStatus(dto.getStatus());
			if (disabling) {
				user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
				refreshTokenService.revokeAll(user.getUsername());
			}
		}
		if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
			validatePassword(dto.getPassword());
			user.setPassword(this.passwordEncoder.encode(dto.getPassword()));
			user.setPasswordChangedAt(LocalDateTime.now());
			user.setMustChangePassword(false);
		}
		user.setLastUpdateTime(LocalDateTime.now());
		if (dto.getRoleIds() != null) {
			Set<Role> roles = new HashSet<>(this.roleRepository.findAllById(dto.getRoleIds()));
			user.setRoles(roles);
			user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
			refreshTokenService.revokeAll(user.getUsername());
		}
		this.adminUserRepository.save(user);
	}

	@Transactional
	public void delete(String id) {
		AdminUser user = this.adminUserRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("user not found"));
		user.setStatus(0); user.setAccountLocked(true); this.adminUserRepository.save(user);
		user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1); this.adminUserRepository.save(user);
		this.refreshTokenService.revokeAll(user.getUsername());
	}

	private void ensureUsernameAvailable(String username, String currentId) {
		if (username == null || username.isBlank()) return;
		AdminUser existing = adminUserRepository.findByUsername(username.trim());
		if (existing != null && !existing.getId().equals(currentId)) throw new IllegalArgumentException("username already exists");
	}

	private void validateEmployeeBinding(String employeeId, String currentUserId) {
		if (employeeId == null || employeeId.isBlank()) return;
		employeeRepository.findById(employeeId).orElseThrow(() -> new IllegalArgumentException("employee not found"));
		adminUserRepository.findByEmployeeId(employeeId).filter(u -> !u.getId().equals(currentUserId))
				.ifPresent(u -> { throw new IllegalArgumentException("employee already has a login account"); });
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 10 || !password.matches(".*[A-Z].*")
				|| !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")) {
			throw new IllegalArgumentException("password must be at least 10 characters and contain upper-case, lower-case and digit");
		}
	}
}
