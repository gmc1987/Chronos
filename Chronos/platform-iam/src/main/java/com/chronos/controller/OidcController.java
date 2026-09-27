package com.chronos.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.service.OidcAuthorizationService;
import com.chronos.service.OidcTokenExchange;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/oidc")
public class OidcController {
	private final OidcAuthorizationService oidc;
	private final OidcTokenExchange tokenExchange;

	@GetMapping("/{sourceCode}/authorize")
	public ResultData<OidcAuthorizationService.AuthorizationStart> authorize(
			@PathVariable String sourceCode,
			@RequestParam String redirectUri,
			@RequestParam String codeChallenge) {
		return ok(oidc.begin(sourceCode, redirectUri, codeChallenge));
	}

	@PostMapping("/{sourceCode}/callback")
	public ResultData<OidcAuthorizationService.OidcIdentity> callback(
			@PathVariable String sourceCode,
			@RequestBody CallbackRequest request) {
		return ok(oidc.complete(sourceCode, request.redirectUri(), request.state(), request.code(),
				request.codeVerifier(), tokenExchange.exchange(sourceCode, request.code(), request.redirectUri(),
						request.codeVerifier())));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}

	public record CallbackRequest(String redirectUri, String state, String code, String codeVerifier) {}
}
