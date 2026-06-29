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
import com.chronos.commons.utils.BeanCopyUtil;
import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.model.vo.AdminUserVO;
import com.chronos.model.vo.RoleVO;
import com.chronos.service.iService.IAdminUserService;

@Service("adminUserService")
public class AdminUserServiceImpl implements IAdminUserService {
	@Autowired
	private IAdminUserRepository adminUserRepository;
	@Autowired
	private IRoleRepository roleRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

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
				RoleVO rvo = RoleVO.builder().id(r.getId()).roleName(r.getRoleName()).description(r.getDescription())
						.build();
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

	@Transactional
	public void save(AdminUserDTO dto) {
		AdminUser user = new AdminUser();
		BeanCopyUtil.copyNonNullProperties(dto, user);
		if (user.getCreateTime() == null) {
			user.setCreateTime(LocalDateTime.now());
		}

		if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
			user.setPassword(this.passwordEncoder.encode(dto.getPassword()));
		}

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
		if (dto.getUsername() != null)
			user.setUsername(dto.getUsername());
		if (dto.getEmail() != null)
			user.setEmail(dto.getEmail());
		if (dto.getStatus() != null)
			user.setStatus(dto.getStatus());
		if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
			user.setPassword(this.passwordEncoder.encode(dto.getPassword()));
		}
		user.setLastUpdateTime(LocalDateTime.now());
		if (dto.getRoleIds() != null) {
			Set<Role> roles = new HashSet<>(this.roleRepository.findAllById(dto.getRoleIds()));
			user.setRoles(roles);
		}
		this.adminUserRepository.save(user);
	}

	@Transactional
	public void delete(String id) {
		this.adminUserRepository.deleteById(id);
	}
}
