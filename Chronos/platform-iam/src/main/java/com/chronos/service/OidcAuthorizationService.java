package com.chronos.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.Idao.IIdentitySourceRepository;
import com.chronos.Idao.IOidcAuthorizationStateRepository;
import com.chronos.model.pojo.IdentitySource;
import com.chronos.model.pojo.OidcAuthorizationState;

@Service
public class OidcAuthorizationService {
	private static final Duration STATE_TTL = Duration.ofMinutes(5);
	private final IIdentitySourceRepository sources;
	private final IOidcAuthorizationStateRepository states;
	private final ObjectMapper objectMapper;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

	@org.springframework.beans.factory.annotation.Autowired
	public OidcAuthorizationService(IIdentitySourceRepository sources,
			IOidcAuthorizationStateRepository states, ObjectMapper objectMapper) {
		this.sources = sources;
		this.states = states;
		this.objectMapper = objectMapper;
	}

	/** Compatibility constructor for focused unit tests without a persistence context. */
	public OidcAuthorizationService(IIdentitySourceRepository sources) {
		this.sources = sources;
		this.states = null;
		this.objectMapper = new ObjectMapper();
	}

	public AuthorizationStart begin(String sourceCode, String redirectUri, String codeChallenge) {
		IdentitySource source = source(sourceCode);
		validateRedirectUri(redirectUri);
		validateConfiguredRedirect(source, redirectUri);
		validateCodeChallenge(codeChallenge);
		String state = randomToken();
		String nonce = randomToken();
		Instant expiresAt = Instant.now().plus(STATE_TTL);
		if (states == null) {
			pending.put(state, new PendingAuthorization(source.getSourceCode(), redirectUri, codeChallenge, nonce,
					expiresAt));
		} else {
			OidcAuthorizationState authorizationState = new OidcAuthorizationState();
			authorizationState.setState(state);
			authorizationState.setSourceCode(source.getSourceCode());
			authorizationState.setRedirectUri(redirectUri);
			authorizationState.setCodeChallenge(codeChallenge);
			authorizationState.setNonce(nonce);
			authorizationState.setExpiresAt(localTime(expiresAt));
			states.save(authorizationState);
		}
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
		PendingAuthorization expected = validateCallback(sourceCode, redirectUri, state, codeVerifier);
		IdentitySource source = source(sourceCode);
		if (!source.getIssuerUrl().equals(tokenClaims.issuer()) || !expected.nonce().equals(tokenClaims.nonce())
				|| blank(tokenClaims.subject())) {
			throw new IllegalArgumentException("OIDC token claims are invalid");
		}
		if (states == null) {
			if (!pending.remove(state, expected)) {
				throw new IllegalArgumentException("OIDC state is invalid or already used");
			}
		} else if (states.consume(state, LocalDateTime.now(java.time.ZoneOffset.UTC)) != 1) {
			throw new IllegalArgumentException("OIDC state is invalid or already used");
		}
		return new OidcIdentity(source.getId(), tokenClaims.subject());
	}

	/**
	 * Validates the callback binding before spending an authorization code at the
	 * provider. State is consumed only after the token claims are verified.
	 */
	public void validateCallbackRequest(String sourceCode, String redirectUri, String state, String codeVerifier) {
		validateCallback(sourceCode, redirectUri, state, codeVerifier);
	}

	private PendingAuthorization validateCallback(String sourceCode, String redirectUri, String state,
			String codeVerifier) {
		if (blank(sourceCode) || blank(redirectUri) || blank(state) || blank(codeVerifier)) {
			throw new IllegalArgumentException("OIDC callback is incomplete");
		}
		PendingAuthorization expected = states == null ? pending.get(state) : loadPersisted(state);
		if (expected == null || !expected.expiresAt().isAfter(Instant.now())) {
			throw new IllegalArgumentException("OIDC state is invalid or already used");
		}
		if (!expected.sourceCode().equals(sourceCode) || !expected.redirectUri().equals(redirectUri)
				|| !MessageDigest.isEqual(expected.codeChallenge().getBytes(StandardCharsets.US_ASCII),
						s256(codeVerifier).getBytes(StandardCharsets.US_ASCII))) {
			throw new IllegalArgumentException("OIDC state or PKCE verification failed");
		}
		return expected;
	}

	private PendingAuthorization loadPersisted(String state) {
		OidcAuthorizationState persisted = states.findById(state).orElse(null);
		if (persisted == null) {
			return null;
		}
		return new PendingAuthorization(persisted.getSourceCode(), persisted.getRedirectUri(),
				persisted.getCodeChallenge(), persisted.getNonce(),
				persisted.getExpiresAt().toInstant(java.time.ZoneOffset.UTC));
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

	private void validateConfiguredRedirect(IdentitySource source, String redirectUri) {
		try {
			JsonNode configured = objectMapper.readTree(
					blank(source.getConfigJson()) ? "{}" : source.getConfigJson());
			JsonNode redirectUris = configured == null ? null : configured.get("redirectUris");
			if (redirectUris == null || !redirectUris.isArray()
					|| java.util.stream.StreamSupport.stream(redirectUris.spliterator(), false)
							.noneMatch(value -> redirectUri.equals(value.asText()))) {
				throw new IllegalArgumentException("redirect URI is not registered for this identity source");
			}
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalArgumentException("identity source redirect URI configuration is invalid", ex);
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

	private static LocalDateTime localTime(Instant instant) {
		return LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
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
