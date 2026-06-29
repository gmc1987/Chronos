package com.chronos.controller;

import com.chronos.Idao.IConsumerUserRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.ConsumerUserDTO;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.security.JwtUtil;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/consumer/users" })
public class ConsumerAuthController {
	@Autowired
	private IConsumerUserRepository consumerUserRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private IRefreshTokenService refreshTokenService;
	@Autowired
	private IAuditLogService auditLogService;

	@PostMapping({ "/login" })
	@ResponseStatus(HttpStatus.OK)
	public ResultData<Map<String, String>> login(@RequestBody ConsumerUserDTO dto) {
		String account = dto.getUsername();
		ConsumerUser u = null;
		if (account != null && !account.isBlank()) {
			u = this.consumerUserRepository.findByUsername(account);
			if (u == null) {
				u = this.consumerUserRepository.findByPhone(account);
			}
		}
		if (u == null && dto.getPhone() != null && !dto.getPhone().isBlank()) {
			u = this.consumerUserRepository.findByPhone(dto.getPhone());
		}
		if (u == null) {
			return ResultData.<Map<String, String>>builder().code("401").msg("invalid credentials").data(null).build();
		}
		if (!this.passwordEncoder.matches(dto.getPassword(), u.getPassword())) {
			return ResultData.<Map<String, String>>builder().code("401").msg("invalid credentials").data(null).build();
		}

		Map<String, Object> claims = new HashMap<>();
		claims.put("type", "consumer");
		String access = this.jwtUtil.generateAccessToken(u.getUsername(), claims);
		String refresh = this.jwtUtil.generateRefreshToken(u.getUsername(), claims);

		Claims parsed = this.jwtUtil.parseToken(refresh);
		LocalDateTime expiry = Instant.ofEpochMilli(parsed.getExpiration().getTime()).atZone(ZoneId.systemDefault())
				.toLocalDateTime();
		this.refreshTokenService.create(refresh, u.getUsername(), expiry);
		this.auditLogService.log(u.getUsername(), "CONSUMER_LOGIN", "consumer login");
		Map<String, String> data = new HashMap<>();
		data.put("accessToken", access);
		data.put("refreshToken", refresh);
		return ResultData.<Map<String, String>>builder().code("200").msg("ok").data(data).build();
	}
}
