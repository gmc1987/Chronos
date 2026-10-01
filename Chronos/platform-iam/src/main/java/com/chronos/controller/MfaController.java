package com.chronos.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.AdminUser;
import com.chronos.security.JwtUtil;
import com.chronos.service.MfaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/mfa")
public class MfaController {
	private final MfaService mfaService;
	private final IAdminUserRepository users;
	private final JwtUtil jwtUtil;

	@PostMapping("/enroll")
	@PreAuthorize("isAuthenticated()")
	public ResultData<MfaService.Enrollment> enroll(Principal principal, @RequestBody EnrollmentRequest request) {
		return ok(mfaService.enroll(principal.getName(), request.totpSecret(), principal.getName()));
	}

	@PostMapping("/verify")
	@PreAuthorize("isAuthenticated()")
	public ResultData<Map<String, String>> verify(Principal principal, @RequestBody VerificationRequest request) {
		boolean valid = request.recoveryCode() == null
				? mfaService.verifyTotp(principal.getName(), request.code(), principal.getName())
				: mfaService.verifyRecoveryCode(principal.getName(), request.recoveryCode(), principal.getName());
		if (!valid) {
			return ResultData.<Map<String, String>>builder().code("401").msg("invalid MFA code").data(null).build();
		}
		AdminUser user = users.findByUsername(principal.getName());
		String token = jwtUtil.generateAccessToken(user.getUsername(),
				Map.of("tokenVersion", user.getTokenVersion() == null ? 0 : user.getTokenVersion(),
						"mfaVerified", true));
		return ok(Map.of("accessToken", token));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}

	public record EnrollmentRequest(String totpSecret) {}
	public record VerificationRequest(String code, String recoveryCode) {}
}
