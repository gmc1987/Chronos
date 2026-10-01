package com.chronos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IConsumerUserRepository;
import com.chronos.Idao.IMenuRepository;
import com.chronos.Idao.IPermissionRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.Idao.IRolePermissionRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.dto.ConsumerUserDTO;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.model.pojo.Role;
import com.chronos.security.JwtUtil;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

class AuthTokenIssuanceTest {
	private JwtUtil jwt;
	private IRefreshTokenService refreshTokens;

	@BeforeEach
	void setUp() {
		jwt = new JwtUtil(new MockEnvironment()
				.withProperty("security.jwt.secret", "test-secret-that-is-long-enough-for-hs256"));
		refreshTokens = mock(IRefreshTokenService.class);
	}

	@Test
	void adminLoginUsesDifferentRefreshJtiForConsecutiveCalls() {
		AuthController controller = new AuthController();
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		AdminUser admin = new AdminUser();
		admin.setUsername("admin");
		admin.setPassword("encoded");
		admin.setStatus(1);
		admin.setTokenVersion(0);
		admin.setRoles(new java.util.HashSet<Role>());
		when(users.findByUsername("admin")).thenReturn(admin);
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		when(authenticationManager.authenticate(any())).thenReturn(
				new UsernamePasswordAuthenticationToken(
						User.withUsername("admin").password("encoded").authorities(List.of()).build(),
						null, List.of()));
		ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
		ReflectionTestUtils.setField(controller, "passwordEncoder", mock(PasswordEncoder.class));
		ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
		ReflectionTestUtils.setField(controller, "adminUserRepository", users);
		ReflectionTestUtils.setField(controller, "roleMenuPermissionRepository", mock(IRoleMenuPermissionRepository.class));
		ReflectionTestUtils.setField(controller, "rolePermissionRepository", mock(IRolePermissionRepository.class));
		ReflectionTestUtils.setField(controller, "permissionRepository", mock(IPermissionRepository.class));
		ReflectionTestUtils.setField(controller, "menuRepository", mock(IMenuRepository.class));
		ReflectionTestUtils.setField(controller, "refreshTokenService", refreshTokens);
		ReflectionTestUtils.setField(controller, "auditLogService", mock(IAuditLogService.class));

		AdminUserDTO request = new AdminUserDTO();
		request.setUsername("admin");
		request.setPassword("password");
		HttpServletRequest servletRequest = mock(HttpServletRequest.class);
		when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
		String first = tokenFrom(controller.login(request, servletRequest));
		String second = tokenFrom(controller.login(request, servletRequest));

		assertThat(jwt.parseToken(first).getId()).isNotEqualTo(jwt.parseToken(second).getId());
	}

	@Test
	void consumerLoginUsesDifferentRefreshJtiForConsecutiveCalls() {
		ConsumerAuthController controller = new ConsumerAuthController();
		IConsumerUserRepository users = mock(IConsumerUserRepository.class);
		ConsumerUser user = new ConsumerUser();
		user.setUsername("student");
		user.setPassword("encoded");
		when(users.findByUsername("student")).thenReturn(user);
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		when(encoder.matches("password", "encoded")).thenReturn(true);
		ReflectionTestUtils.setField(controller, "consumerUserRepository", users);
		ReflectionTestUtils.setField(controller, "passwordEncoder", encoder);
		ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
		ReflectionTestUtils.setField(controller, "refreshTokenService", refreshTokens);
		ReflectionTestUtils.setField(controller, "auditLogService", mock(IAuditLogService.class));
		ConsumerUserDTO request = new ConsumerUserDTO();
		request.setUsername("student");
		request.setPassword("password");

		String first = tokenFrom(controller.login(request).getData(), "refreshToken");
		String second = tokenFrom(controller.login(request).getData(), "refreshToken");

		assertThat(jwt.parseToken(first).getId()).isNotEqualTo(jwt.parseToken(second).getId());
	}

	private String tokenFrom(ResultData<Map<String, Object>> response) {
		return (String) response.getData().get("refreshToken");
	}

	private String tokenFrom(Map<String, String> data, String key) {
		return data.get(key);
	}
}
