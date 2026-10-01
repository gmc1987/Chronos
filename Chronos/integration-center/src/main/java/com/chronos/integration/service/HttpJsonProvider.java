package com.chronos.integration.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public interface HttpJsonProvider {
	default String providerCode() {
		return "HTTP_JSON";
	}

	void validateConfig(String configJson);

	default void validateEndpoint(String baseUrl, String allowedHost) {
	}

	void testConnection(String baseUrl, int timeoutMs);

	List<Map<String, Object>> fetchPage(String baseUrl, String path, String cursor, int batchSize, String idempotencyKey);

	default ProviderPage fetchPagePage(String baseUrl, String path, String cursor, int batchSize,
			String idempotencyKey, Map<String, String> headers) {
		return new ProviderPage(fetchPage(baseUrl, path, cursor, batchSize, idempotencyKey), null);
	}

	Map<String, Object> map(JsonNode source, List<com.chronos.integration.model.FieldMapping> mappings);
	void acknowledge(String baseUrl, String path, String idempotencyKey);

	record ProviderPage(List<Map<String, Object>> records, String nextCursor) {
		public ProviderPage {
			records = records == null ? List.of() : List.copyOf(records);
		}
	}
}
