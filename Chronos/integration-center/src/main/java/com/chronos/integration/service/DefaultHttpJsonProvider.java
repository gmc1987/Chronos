package com.chronos.integration.service;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DefaultHttpJsonProvider implements HttpJsonProvider {
	private final WebClient.Builder builder;

	@Override
	public void validateConfig(String configJson) {
		try {
			com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(configJson);
		} catch (Exception ex) {
			throw new IntegrationBoundaryException("INVALID_PROVIDER_CONFIG", "HTTP JSON 配置必须是合法 JSON");
		}
	}

	@Override
	public void testConnection(String baseUrl, int timeoutMs) {
		builder.build().get().uri(URI.create(baseUrl)).retrieve().toBodilessEntity()
				.timeout(Duration.ofMillis(Math.max(1000, timeoutMs))).block();
	}

	@Override
	public List<Map<String, Object>> fetchPage(String baseUrl, String path, String cursor,
			int batchSize, String idempotencyKey) {
		JsonNode root = builder.build().get().uri(URI.create(baseUrl + path))
				.header("Idempotency-Key", idempotencyKey).retrieve().bodyToMono(JsonNode.class)
				.timeout(Duration.ofSeconds(30)).block();
		List<Map<String, Object>> result = new ArrayList<>();
		if (root == null) return result;
		JsonNode values = root.isArray() ? root : root.path("data");
		if (!values.isArray()) return result;
		for (JsonNode item : values) {
			if (result.size() >= batchSize) break;
			result.add(new LinkedHashMap<>(new com.fasterxml.jackson.databind.ObjectMapper()
					.convertValue(item, Map.class)));
		}
		return result;
	}

	@Override
	public Map<String, Object> map(JsonNode source, List<com.chronos.integration.model.FieldMapping> mappings) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (var mapping : mappings) {
			JsonNode value = source.at(mapping.getSourcePath().startsWith("/")
					? mapping.getSourcePath() : "/" + mapping.getSourcePath().replace('.', '/'));
			if ((value == null || value.isMissingNode() || value.isNull()) && Boolean.TRUE.equals(mapping.getRequired())) {
				throw new IntegrationBoundaryException("REQUIRED_MAPPING_MISSING",
						"required source field is missing: " + mapping.getSourcePath());
			}
			result.put(mapping.getTargetField(), value == null || value.isNull() ? null
					: value.isValueNode() ? value.asText() : value);
		}
		return result;
	}

	@Override
	public void acknowledge(String baseUrl, String path, String idempotencyKey) {
		builder.build().post().uri(URI.create(baseUrl + path)).header("Idempotency-Key", idempotencyKey)
				.retrieve().toBodilessEntity().timeout(Duration.ofSeconds(30)).block();
	}
}
