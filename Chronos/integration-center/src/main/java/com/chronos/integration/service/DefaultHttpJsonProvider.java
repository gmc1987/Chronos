package com.chronos.integration.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DefaultHttpJsonProvider implements HttpJsonProvider {
	private final WebClient.Builder builder;
	private final ObjectMapper objectMapper;
	private final IntegrationNetworkPolicy networkPolicy;

	public DefaultHttpJsonProvider(WebClient.Builder builder, ObjectMapper objectMapper,
			IntegrationNetworkPolicy networkPolicy) {
		this.builder = builder;
		this.objectMapper = objectMapper;
		this.networkPolicy = networkPolicy;
	}

	@Override
	public void validateConfig(String configJson) {
		try {
			JsonNode root = objectMapper.readTree(configJson);
			if (root == null || !root.isObject()) {
				throw new IntegrationBoundaryException("INVALID_PROVIDER_CONFIG",
						"HTTP JSON 配置必须是 JSON object");
			}
		} catch (IntegrationBoundaryException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IntegrationBoundaryException("INVALID_PROVIDER_CONFIG", "HTTP JSON 配置必须是合法 JSON");
		}
	}

	@Override
	public void validateEndpoint(String baseUrl, String allowedHost) {
		networkPolicy.validateBaseUrl(baseUrl, allowedHost);
	}

	@Override
	public void testConnection(String baseUrl, int timeoutMs) {
		URI uri = networkPolicy.validateBaseUrl(baseUrl, null);
		builder.build().get().uri(uri).retrieve().toBodilessEntity()
				.timeout(Duration.ofMillis(Math.max(1000, timeoutMs))).block();
	}

	@Override
	public List<Map<String, Object>> fetchPage(String baseUrl, String path, String cursor,
			int batchSize, String idempotencyKey) {
		return fetchPagePage(baseUrl, path, cursor, batchSize, idempotencyKey, Map.of()).records();
	}

	@Override
	public ProviderPage fetchPagePage(String baseUrl, String path, String cursor,
			int batchSize, String idempotencyKey, Map<String, String> headers) {
		URI endpoint = networkPolicy.resolve(baseUrl, withCursor(path, cursor), null);
		var request = builder.build().get().uri(endpoint);
		request.headers(target -> {
			headers.forEach(target::set);
			if (idempotencyKey != null && !idempotencyKey.isBlank()) {
				target.set("Idempotency-Key", idempotencyKey);
			}
		});
		JsonNode root = request.retrieve().bodyToMono(JsonNode.class)
				.timeout(Duration.ofSeconds(30)).block();
		List<Map<String, Object>> result = new ArrayList<>();
		if (root == null) {
			return new ProviderPage(result, null);
		}
		JsonNode values = root.isArray() ? root : root.path("data");
		if (!values.isArray()) {
			throw new IntegrationBoundaryException("INVALID_PROVIDER_RESPONSE",
					"HTTP JSON provider response must contain an array");
		}
		for (JsonNode item : values) {
			if (result.size() >= Math.max(1, batchSize)) {
				break;
			}
			result.add(new LinkedHashMap<>(objectMapper.convertValue(item, Map.class)));
		}
		return new ProviderPage(result, firstText(root, "nextCursor", "next_cursor", "cursor"));
	}

	@Override
	public Map<String, Object> map(JsonNode source,
			List<com.chronos.integration.model.FieldMapping> mappings) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (var mapping : mappings) {
			JsonNode value = source.at(mapping.getSourcePath().startsWith("/")
					? mapping.getSourcePath() : "/" + mapping.getSourcePath().replace('.', '/'));
			if ((value == null || value.isMissingNode() || value.isNull())
					&& Boolean.TRUE.equals(mapping.getRequired())) {
				throw new IntegrationBoundaryException("REQUIRED_MAPPING_MISSING",
						"required source field is missing: " + mapping.getSourcePath());
			}
			result.put(mapping.getTargetField(), transform(value, mapping.getTransformCode()));
		}
		return result;
	}

	@Override
	public void acknowledge(String baseUrl, String path, String idempotencyKey) {
		URI endpoint = networkPolicy.resolve(baseUrl, path, null);
		builder.build().post().uri(endpoint).header("Idempotency-Key", idempotencyKey)
				.retrieve().toBodilessEntity().timeout(Duration.ofSeconds(30)).block();
	}

	private static String withCursor(String path, String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return path;
		}
		return path + (path.contains("?") ? "&" : "?") + "cursor="
				+ URLEncoder.encode(cursor, StandardCharsets.UTF_8);
	}

	private static String firstText(JsonNode root, String... names) {
		if (root == null || root.isArray()) {
			return null;
		}
		for (String name : names) {
			JsonNode value = root.get(name);
			if (value != null && value.isValueNode() && !value.isNull()) {
				return value.asText();
			}
		}
		return null;
	}

	private static Object transform(JsonNode value, String transformCode) {
		if (value == null || value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.isValueNode() ? value.asText() : value.toString();
		if (transformCode == null || transformCode.isBlank()) {
			return value.isValueNode() ? text : value;
		}
		return switch (transformCode.trim().toUpperCase(java.util.Locale.ROOT)) {
		case "TRIM" -> text.trim();
		case "LOWER" -> text.toLowerCase(java.util.Locale.ROOT);
		case "UPPER" -> text.toUpperCase(java.util.Locale.ROOT);
		case "STRING" -> text;
		case "INTEGER" -> parseInteger(text);
		default -> throw new IntegrationBoundaryException("UNSUPPORTED_TRANSFORM",
				"mapping transform is not supported");
		};
	}

	private static Integer parseInteger(String value) {
		try {
			return Integer.valueOf(value.trim());
		} catch (NumberFormatException ex) {
			throw new IntegrationBoundaryException("INVALID_TRANSFORM_VALUE",
					"mapping value is not a valid integer");
		}
	}
}
