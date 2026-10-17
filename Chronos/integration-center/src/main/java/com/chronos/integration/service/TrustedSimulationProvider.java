package com.chronos.integration.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Deterministic provider for acceptance and disaster-recovery drills.
 *
 * <p>Simulation data lives in connector configuration and never performs a
 * network request. This keeps SSRF protection intact while still exercising
 * mapping, cursor, dead-letter, replay and reconciliation production paths.</p>
 */
@Component
public class TrustedSimulationProvider {
	public static final String PROVIDER_CODE = "TRUSTED_SIMULATION";

	private final ObjectMapper objectMapper;

	public TrustedSimulationProvider(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void validateConfig(String configJson) {
		JsonNode root = root(configJson);
		if (!root.path("pages").isObject()) {
			throw new IntegrationBoundaryException("INVALID_SIMULATION_CONFIG",
					"可信仿真配置必须包含 pages object");
		}
	}

	public HttpJsonProvider.ProviderPage fetchPage(String configJson, String cursor, int batchSize) {
		JsonNode root = root(configJson);
		String key = cursor == null || cursor.isBlank() ? "START" : cursor;
		JsonNode configuredFailure = root.path("failOnCursor");
		if (configuredFailure.isTextual() && key.equals(configuredFailure.asText())) {
			throw new IntegrationBoundaryException("SIMULATED_PROVIDER_FAILURE",
					root.path("failureMessage").asText("可信仿真上游故障"));
		}
		JsonNode page = root.path("pages").path(key);
		if (!page.isObject()) {
			return new HttpJsonProvider.ProviderPage(List.of(), null);
		}
		JsonNode records = page.path("records");
		if (!records.isArray()) {
			throw new IntegrationBoundaryException("INVALID_SIMULATION_CONFIG",
					"可信仿真页面 records 必须是 array");
		}
		List<Map<String, Object>> result = new ArrayList<>();
		for (JsonNode record : records) {
			if (result.size() >= Math.max(1, batchSize)) {
				break;
			}
			result.add(new LinkedHashMap<>(objectMapper.convertValue(record, Map.class)));
		}
		JsonNode next = page.get("nextCursor");
		return new HttpJsonProvider.ProviderPage(result,
				next == null || next.isNull() || !next.isValueNode() ? null : next.asText());
	}

	private JsonNode root(String configJson) {
		try {
			JsonNode root = objectMapper.readTree(configJson);
			if (root == null || !root.isObject()) {
				throw new IntegrationBoundaryException("INVALID_SIMULATION_CONFIG",
						"可信仿真配置必须是 JSON object");
			}
			return root;
		} catch (IntegrationBoundaryException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IntegrationBoundaryException("INVALID_SIMULATION_CONFIG",
					"可信仿真配置必须是合法 JSON");
		}
	}
}
