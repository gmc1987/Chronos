package com.chronos.service;

/**
 * Compatibility implementation for deployments that deliberately do not
 * enable OIDC. The normal application bean is HttpOidcTokenExchange.
 */
public class UnsupportedOidcTokenExchange implements OidcTokenExchange {
	@Override
	public OidcAuthorizationService.OidcTokenClaims exchange(
			String sourceCode, String code, String redirectUri, String codeVerifier) {
		throw new IllegalStateException("OIDC provider token exchange is not configured");
	}
}
