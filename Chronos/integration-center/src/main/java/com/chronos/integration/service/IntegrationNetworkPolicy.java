package com.chronos.integration.service;

import java.net.InetAddress;
import java.net.IDN;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates connector destinations before every outbound request.
 *
 * <p>The connector host is an allowlist entry in its own right. An optional
 * application allowlist can further narrow the destinations. Redirects are
 * disabled by the HTTP client, so a validated destination cannot be replaced
 * by an unvalidated redirect target.</p>
 */
@Component
public class IntegrationNetworkPolicy {
	private static final Set<String> METADATA_HOSTS = Set.of(
			"metadata", "metadata.google.internal", "instance-data",
			"169.254.169.254", "100.100.100.200");

	private final Set<String> configuredHosts;

	public IntegrationNetworkPolicy(
			@Value("${chronos.integration.allowed-hosts:}") String allowedHosts) {
		this.configuredHosts = parseHosts(allowedHosts);
	}

	public URI validateBaseUrl(String rawUrl, String connectorAllowlist) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw invalid("base URL is required");
		}
		try {
			URI uri = URI.create(rawUrl.trim());
			if (!"http".equalsIgnoreCase(uri.getScheme())
					&& !"https".equalsIgnoreCase(uri.getScheme())) {
				throw invalid("base URL must use HTTP(S)");
			}
			if (uri.getHost() == null || uri.getUserInfo() != null
					|| uri.getQuery() != null || uri.getFragment() != null) {
				throw invalid("base URL must contain only an HTTP(S) host and optional path");
			}
			URI normalized = uri.normalize();
			if (!normalized.equals(uri) && normalized.getPath() != null
					&& normalized.getPath().contains("..")) {
				throw invalid("base URL path is not safe");
			}
			validateHost(uri.getHost(), connectorAllowlist);
			return uri;
		} catch (IllegalArgumentException ex) {
			if (ex instanceof IntegrationBoundaryException boundary) {
				throw boundary;
			}
			throw invalid("base URL is not a valid HTTP(S) URL");
		}
	}

	public URI resolve(String baseUrl, String requestPath, String connectorAllowlist) {
		if (requestPath == null || requestPath.isBlank() || !requestPath.startsWith("/")
				|| requestPath.contains("\r") || requestPath.contains("\n")) {
			throw invalid("request path must be a relative HTTP path");
		}
		URI base = validateBaseUrl(baseUrl, connectorAllowlist);
		try {
			URI path = URI.create(requestPath);
			if (path.isAbsolute() || path.getUserInfo() != null || path.getHost() != null) {
				throw invalid("request path must be a relative HTTP path");
			}
			URI resolved = base.resolve(path);
			if (!base.getScheme().equalsIgnoreCase(resolved.getScheme())
					|| !base.getHost().equalsIgnoreCase(resolved.getHost())
					|| base.getPort() != resolved.getPort()) {
				throw invalid("request path changed the connector destination");
			}
			return resolved;
		} catch (IllegalArgumentException ex) {
			if (ex instanceof IntegrationBoundaryException boundary) {
				throw boundary;
			}
			throw invalid("request path is not a valid relative HTTP path");
		}
	}

	private void validateHost(String rawHost, String connectorAllowlist) {
		String host = normalizeHost(rawHost);
		Set<String> connectorHosts = parseHosts(connectorAllowlist);
		if (!configuredHosts.isEmpty()
				&& configuredHosts.stream().noneMatch(value -> matches(value, host))) {
			throw invalid("connector host is not in the network allowlist");
		}
		if (!connectorHosts.isEmpty()
				&& connectorHosts.stream().noneMatch(value -> matches(value, host))) {
			throw invalid("connector host is not in the network allowlist");
		}
		if (METADATA_HOSTS.contains(host) || host.endsWith(".localhost")
				|| host.equals("localhost") || host.endsWith(".local")
				|| isLiteralPrivateAddress(host)) {
			throw invalid("connector destination is not allowed");
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress()
						|| address.isLinkLocalAddress() || address.isSiteLocalAddress()
						|| address.isMulticastAddress()) {
					throw invalid("connector destination resolves to a private address");
				}
			}
		} catch (UnknownHostException ex) {
			throw invalid("connector host cannot be resolved");
		}
	}

	private static boolean matches(String allowlistEntry, String host) {
		String entry = normalizeHost(allowlistEntry);
		return entry.startsWith("*.") ? host.endsWith(entry.substring(1))
				&& !host.equals(entry.substring(2)) : host.equals(entry);
	}

	private static Set<String> parseHosts(String raw) {
		if (raw == null || raw.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.map(IntegrationNetworkPolicy::normalizeHost)
				.collect(Collectors.toUnmodifiableSet());
	}

	private static String normalizeHost(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return IDN.toASCII(raw.trim()).toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
	}

	private static boolean isLiteralPrivateAddress(String host) {
		if (!host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
			return false;
		}
		String[] parts = host.split("\\.");
		int first = Integer.parseInt(parts[0]);
		int second = Integer.parseInt(parts[1]);
		return first == 0 || first == 10 || first == 127 || first == 169 && second == 254
				|| first == 172 && second >= 16 && second <= 31
				|| first == 192 && second == 168;
	}

	private static IntegrationBoundaryException invalid(String message) {
		return new IntegrationBoundaryException("INVALID_CONNECTOR_DESTINATION", message);
	}
}
