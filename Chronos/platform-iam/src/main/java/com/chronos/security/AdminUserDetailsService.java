package com.chronos.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IPermissionRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.Idao.IRolePermissionRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.BaseEntity;

@Service("adminUserDetailsService")
public class AdminUserDetailsService implements UserDetailsService {
	@Autowired
	private IAdminUserRepository adminUserRepository;
	@Autowired
	private IRoleMenuPermissionRepository roleMenuPermissionRepository;
	@Autowired
	private IPermissionRepository permissionRepository;
	@Autowired
	private IRolePermissionRepository rolePermissionRepository;

	public AdminUser loadAccount(String username) { return adminUserRepository.findByUsername(username); }

	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AdminUser u = this.adminUserRepository.findByUsername(username);
		if (u == null)
			throw new UsernameNotFoundException("user not found");
		Collection<GrantedAuthority> authorities = new ArrayList<>();
		if (Boolean.TRUE.equals(u.getAccountLocked()) && u.getLockUntil() != null && u.getLockUntil().isBefore(java.time.LocalDateTime.now())) {
			u.setAccountLocked(false); u.setLockUntil(null); u.setFailedLoginAttempts(0); adminUserRepository.save(u);
		}
		if (u.getRoles() != null) {
			authorities.addAll((Collection<? extends GrantedAuthority>) u.getRoles().stream().filter(r -> Integer.valueOf(1).equals(r.getStatus()))
					.map(r -> new SimpleGrantedAuthority("ROLE_" + r.getRoleName())).collect(Collectors.toList()));
			u.getRoles().stream().filter(r -> Integer.valueOf(1).equals(r.getStatus()) && Boolean.TRUE.equals(r.getBuiltIn()) && r.getRoleCode() != null)
					.map(r -> new SimpleGrantedAuthority("ROLE_CODE_" + r.getRoleCode().toUpperCase()))
					.forEach(authorities::add);
			var roleIds = u.getRoles().stream().filter(r -> Integer.valueOf(1).equals(r.getStatus())).map(BaseEntity::getId).toList();
			if (!roleIds.isEmpty()) {
				var permissionIds = rolePermissionRepository.findByRoleIdIn(roleIds).stream()
						.map(r -> r.getPermissionId()).distinct().toList();
				if (permissionIds.isEmpty()) permissionIds = roleMenuPermissionRepository.findByRoleIdIn(roleIds).stream()
						.map(r -> r.getPermissionId()).distinct().toList();
				permissionRepository.findAllById(permissionIds).stream()
						.map(p -> new SimpleGrantedAuthority(p.getPermissionCode()))
						.forEach(authorities::add);
			}
		}
		boolean enabled = Integer.valueOf(1).equals(u.getStatus());
		boolean locked = Boolean.TRUE.equals(u.getAccountLocked());
		return User.withUsername(u.getUsername()).password(u.getPassword()).authorities(authorities)
				.disabled(!enabled).accountLocked(locked).build();
	}
}
