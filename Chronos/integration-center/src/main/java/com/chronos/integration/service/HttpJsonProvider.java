package com.chronos.integration.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public interface HttpJsonProvider {
	void validateConfig(String configJson);
	void testConnection(String baseUrl, int timeoutMs);
	List<Map<String, Object>> fetchPage(String baseUrl, String path, String cursor, int batchSize, String idempotencyKey);
	Map<String, Object> map(JsonNode source, List<com.chronos.integration.model.FieldMapping> mappings);
	void acknowledge(String baseUrl, String path, String idempotencyKey);
}
