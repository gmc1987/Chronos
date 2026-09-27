package com.chronos.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UnsupportedOidcTokenExchange implements OidcTokenExchange {
	@Override
	public OidcAuthorizationService.OidcTokenClaims exchange(
			String sourceCode, String code, String redirectUri, String codeVerifier) {
		throw new IllegalStateException("OIDC provider token exchange is not configured");
	}
}
