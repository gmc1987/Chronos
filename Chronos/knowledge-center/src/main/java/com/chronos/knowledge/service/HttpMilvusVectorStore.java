package com.chronos.knowledge.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Milvus REST adapter. A collection is derived from the KB id, preventing
 * cross-tenant retrieval.
 */
@Component
public class HttpMilvusVectorStore implements MilvusVectorStore {
	private final String endpoint;
	private final RestClient client;

	public HttpMilvusVectorStore(@Value("${chronos.milvus.endpoint:}") String endpoint) {
		this.endpoint = endpoint == null ? "" : endpoint.replaceAll("/+$", "");
		this.client = RestClient.builder().baseUrl(this.endpoint).build();
	}

	@Override public boolean available() { return !endpoint.isBlank(); }
	@Override public void upsert(String base, String chunk, float[] vector) {
		if (!available()) throw new IllegalStateException("Milvus endpoint 未配置");
		ensureCollection(collection(base), vector.length);
		client.post().uri("/v2/vectordb/entities/upsert")
				.body(java.util.Map.of("collectionName", collection(base), "data",
						List.of(java.util.Map.of("id", chunk, "vector", vector)))).retrieve().toBodilessEntity();
	}
	@Override public List<String> search(String base, float[] vector, int limit) {
		if (!available()) throw new IllegalStateException("Milvus endpoint 未配置");
		ensureCollection(collection(base), vector.length);
		JsonNode root = client.post().uri("/v2/vectordb/entities/search")
				.body(java.util.Map.of(
						"collectionName", collection(base),
						"data", List.of(vector),
						"annsField", "vector",
						"limit", limit,
						"outputFields", List.of("id"),
						"searchParams", java.util.Map.of("metric_type", "COSINE")))
				.retrieve().body(JsonNode.class);
		if (root == null || !root.has("data") || !root.get("data").isArray()) {
			return List.of();
		}
		java.util.ArrayList<String> ids = new java.util.ArrayList<>();
		for (JsonNode item : root.get("data")) {
			JsonNode id = item.get("id");
			if (id != null && !id.isNull()) {
				ids.add(id.asText());
			}
		}
		return ids;
	}
	@Override public void delete(String base, String chunk) {
		if (!available()) return;
		client.post().uri("/v2/vectordb/entities/delete")
				.body(java.util.Map.of("collectionName", collection(base), "filter", "id == '" + chunk + "'"))
				.retrieve().toBodilessEntity();
	}

	private void ensureCollection(String name, int dimension) {
		try {
			JsonNode describe = client.post().uri("/v2/vectordb/collections/describe")
					.body(java.util.Map.of("collectionName", name))
					.retrieve()
					.body(JsonNode.class);
			if (describe != null && describe.has("code") && describe.get("code").asInt() == 0) {
				return;
			}
		} catch (HttpClientErrorException.NotFound ignored) {
			// The first write creates the collection below.
		}
		client.post().uri("/v2/vectordb/collections/create")
				.body(java.util.Map.of(
						"collectionName", name,
						"schema", java.util.Map.of(
								"enableDynamicField", false,
								"fields", List.of(
										java.util.Map.of(
												"fieldName", "id",
												"dataType", "VarChar",
												"isPrimary", true,
												"elementTypeParams", java.util.Map.of("max_length", "128")),
										java.util.Map.of(
												"fieldName", "vector",
												"dataType", "FloatVector",
												"elementTypeParams", java.util.Map.of("dim", String.valueOf(dimension))))),
						"indexParams", List.of(java.util.Map.of(
								"fieldName", "vector",
								"indexName", "vector_index",
								"metricType", "COSINE",
								"indexType", "AUTOINDEX"))))
				.retrieve().toBodilessEntity();
	}

	private String collection(String id) { return "kb_" + id.replaceAll("[^a-zA-Z0-9_]", "_"); }
}
