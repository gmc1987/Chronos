package com.chronos.service;

public interface OidcTokenExchange {
	OidcAuthorizationService.OidcTokenClaims exchange(
			String sourceCode, String code, String redirectUri, String codeVerifier);
}
