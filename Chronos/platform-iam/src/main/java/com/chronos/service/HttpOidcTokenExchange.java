package com.chronos.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.Idao.IIdentitySourceRepository;
import com.chronos.model.pojo.IdentitySource;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HttpOidcTokenExchange implements OidcTokenExchange {
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
	private final IIdentitySourceRepository sources;
	private final ObjectMapper objectMapper;
	private final OidcClientSecretProvider secrets;
	private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@Override
	public OidcAuthorizationService.OidcTokenClaims exchange(
			String sourceCode, String code, String redirectUri, String codeVerifier) {
		IdentitySource source = sources.findBySourceCode(sourceCode)
				.orElseThrow(() -> new IllegalArgumentException("OIDC identity source not found"));
		if (!"OIDC".equalsIgnoreCase(source.getSourceType())
				|| !"ACTIVE".equalsIgnoreCase(source.getStatus())) {
			throw new IllegalStateException("OIDC identity source is not active");
		}
		if (blank(code) || blank(redirectUri) || blank(codeVerifier)) {
			throw new IllegalArgumentException("OIDC token exchange is incomplete");
		}
		String endpoint = tokenEndpoint(source);
		boolean publicClient = publicClient(source);
		StringBuilder form = new StringBuilder()
				.append(form("grant_type", "authorization_code"))
				.append('&').append(form("code", code))
				.append('&').append(form("redirect_uri", redirectUri))
				.append('&').append(form("client_id", source.getClientId()))
				.append('&').append(form("code_verifier", codeVerifier));
		if (!publicClient) {
			form.append('&').append(form("client_secret", secrets.resolve(source)));
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(REQUEST_TIMEOUT)
					.header("Accept", "application/json")
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(form.toString()))
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("OIDC token endpoint rejected authorization code");
			}
			JsonNode payload = objectMapper.readTree(response.body());
			String idToken = payload == null ? null : payload.path("id_token").asText(null);
			if (blank(idToken)) {
				throw new IllegalStateException("OIDC token response did not contain an id_token");
			}
			var jwt = decoder(source).decode(idToken);
			if (!jwt.getAudience().contains(source.getClientId())) {
				throw new IllegalArgumentException("OIDC id_token audience is invalid");
			}
			return new OidcAuthorizationService.OidcTokenClaims(
					jwt.getIssuer().toString(), jwt.getSubject(), jwt.getClaimAsString("nonce"));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("OIDC token exchange interrupted", interrupted);
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("OIDC token exchange failed", exception);
		}
	}

	private JwtDecoder decoder(IdentitySource source) {
		return decoders.computeIfAbsent(source.getIssuerUrl(), JwtDecoders::fromIssuerLocation);
	}

	private String tokenEndpoint(IdentitySource source) {
		try {
			JsonNode config = objectMapper.readTree(
					blank(source.getConfigJson()) ? "{}" : source.getConfigJson());
			String endpoint = config == null ? null : config.path("tokenEndpoint").asText(null);
			URI tokenUri = URI.create(endpoint == null ? "" : endpoint);
			URI issuer = URI.create(source.getIssuerUrl());
			if (!"https".equalsIgnoreCase(tokenUri.getScheme())
					|| tokenUri.getHost() == null
					|| !tokenUri.getHost().equalsIgnoreCase(issuer.getHost())
					|| tokenUri.getUserInfo() != null || tokenUri.getFragment() != null) {
				throw new IllegalArgumentException("OIDC token endpoint must be HTTPS on the issuer host");
			}
			return tokenUri.toString();
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("OIDC token endpoint configuration is invalid", exception);
		} catch (Exception exception) {
			throw new IllegalArgumentException("OIDC token endpoint configuration is invalid", exception);
		}
	}

	private boolean publicClient(IdentitySource source) {
		try {
			JsonNode config = objectMapper.readTree(
					blank(source.getConfigJson()) ? "{}" : source.getConfigJson());
			return config != null && config.path("publicClient").asBoolean(false);
		} catch (Exception exception) {
			throw new IllegalArgumentException("OIDC source configuration is invalid", exception);
		}
	}

	private static String form(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("OIDC source configuration is incomplete");
		}
		return URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
