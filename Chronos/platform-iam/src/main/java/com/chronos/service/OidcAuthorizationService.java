package com.chronos.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.chronos.Idao.IIdentitySourceRepository;
import com.chronos.model.pojo.IdentitySource;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OidcAuthorizationService {
	private static final Duration STATE_TTL = Duration.ofMinutes(5);
	private final IIdentitySourceRepository sources;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

	public AuthorizationStart begin(String sourceCode, String redirectUri, String codeChallenge) {
		IdentitySource source = source(sourceCode);
		validateRedirectUri(redirectUri);
		validateCodeChallenge(codeChallenge);
		String state = randomToken();
		String nonce = randomToken();
		pending.put(state, new PendingAuthorization(source.getSourceCode(), redirectUri, codeChallenge, nonce,
				Instant.now().plus(STATE_TTL)));
		String authorizationUri = source.getIssuerUrl().replaceAll("/+$", "") + "/authorize"
				+ "?response_type=code&client_id=" + encode(source.getClientId())
				+ "&redirect_uri=" + encode(redirectUri)
				+ "&scope=" + encode("openid profile email")
				+ "&state=" + encode(state)
				+ "&nonce=" + encode(nonce)
				+ "&code_challenge=" + encode(codeChallenge)
				+ "&code_challenge_method=S256";
		return new AuthorizationStart(authorizationUri, state, nonce);
	}

	public OidcIdentity complete(String sourceCode, String redirectUri, String state, String code,
			String codeVerifier, OidcTokenClaims tokenClaims) {
		if (blank(code) || blank(state) || blank(codeVerifier) || tokenClaims == null) {
			throw new IllegalArgumentException("OIDC callback is incomplete");
		}
		PendingAuthorization expected = pending.remove(state);
		if (expected == null || expected.expiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("OIDC state is invalid or already used");
		}
		if (!expected.sourceCode().equals(sourceCode) || !expected.redirectUri().equals(redirectUri)
				|| !MessageDigest.isEqual(expected.codeChallenge().getBytes(StandardCharsets.US_ASCII),
						s256(codeVerifier).getBytes(StandardCharsets.US_ASCII))) {
			throw new IllegalArgumentException("OIDC state or PKCE verification failed");
		}
		IdentitySource source = source(sourceCode);
		if (!source.getIssuerUrl().equals(tokenClaims.issuer()) || !expected.nonce().equals(tokenClaims.nonce())
				|| blank(tokenClaims.subject())) {
			throw new IllegalArgumentException("OIDC token claims are invalid");
		}
		return new OidcIdentity(source.getId(), tokenClaims.subject());
	}

	private IdentitySource source(String sourceCode) {
		IdentitySource source = sources.findBySourceCode(sourceCode)
				.orElseThrow(() -> new IllegalArgumentException("OIDC identity source not found"));
		if (!"OIDC".equalsIgnoreCase(source.getSourceType()) || !"ACTIVE".equalsIgnoreCase(source.getStatus())) {
			throw new IllegalStateException("OIDC identity source is not active");
		}
		URI issuer = URI.create(source.getIssuerUrl());
		if (!"https".equalsIgnoreCase(issuer.getScheme()) || issuer.getHost() == null) {
			throw new IllegalArgumentException("OIDC issuer must be HTTPS");
		}
		return source;
	}

	private static void validateRedirectUri(String redirectUri) {
		URI uri = URI.create(redirectUri == null ? "" : redirectUri);
		if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("redirect URI must use HTTP(S)");
		}
		if (uri.getUserInfo() != null || uri.getFragment() != null) {
			throw new IllegalArgumentException("redirect URI is invalid");
		}
	}

	private static void validateCodeChallenge(String challenge) {
		if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43,128}")) {
			throw new IllegalArgumentException("PKCE S256 code challenge is required");
		}
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String s256(String verifier) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (Exception exception) {
			throw new IllegalStateException("unable to calculate PKCE challenge", exception);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public record AuthorizationStart(String authorizationUri, String state, String nonce) {}
	public record OidcTokenClaims(String issuer, String subject, String nonce) {}
	public record OidcIdentity(String sourceId, String subject) {}
	private record PendingAuthorization(String sourceCode, String redirectUri, String codeChallenge, String nonce,
			Instant expiresAt) {}
}
