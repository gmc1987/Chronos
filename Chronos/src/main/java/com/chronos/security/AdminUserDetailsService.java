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
import com.chronos.model.pojo.AdminUser;

@Service("adminUserDetailsService")
public class AdminUserDetailsService implements UserDetailsService {
	@Autowired
	private IAdminUserRepository adminUserRepository;

	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AdminUser u = this.adminUserRepository.findByUsername(username);
		if (u == null)
			throw new UsernameNotFoundException("user not found");
		Collection<GrantedAuthority> authorities = new ArrayList<>();
		if (u.getRoles() != null) {
			authorities.addAll((Collection<? extends GrantedAuthority>) u.getRoles().stream()
					.map(r -> new SimpleGrantedAuthority("ROLE_" + r.getRoleName())).collect(Collectors.toList()));
		}
		return (UserDetails) new User(u.getUsername(), u.getPassword(), authorities);
	}
}
