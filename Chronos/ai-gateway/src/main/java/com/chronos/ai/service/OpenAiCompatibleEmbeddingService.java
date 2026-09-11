package com.chronos.ai.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.fasterxml.jackson.databind.JsonNode;

/** OpenAI-compatible /v1/embeddings client; works with most cloud providers. */
@Service
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {
	private final AiModelRepository models;

	public OpenAiCompatibleEmbeddingService(AiModelRepository models) {
		this.models = models;
	}

	@Override
	public List<float[]> embed(List<String> texts, String modelId) {
		AiModel model = modelId == null || modelId.isBlank()
				? models.findFirstDefaultEmbedding().orElseThrow(() -> new AiModelConfigurationException("未配置默认 Embedding 模型"))
				: models.findById(modelId).orElseThrow(() -> new AiModelConfigurationException("Embedding 模型不存在"));
		if (!"EMBEDDING".equalsIgnoreCase(model.getModelType()) || !Integer.valueOf(1).equals(model.getStatus())) {
			throw new AiModelConfigurationException("Embedding 模型未启用");
		}
		if (model.getApiKey() == null || model.getApiKey().isBlank()) {
			throw new AiModelConfigurationException("Embedding API Key 未配置");
		}
		String base = model.getBaseUrl();
		if (base == null || base.isBlank()) base = "https://api.openai.com";
		String endpoint = base.replaceAll("/+$", "") + (base.endsWith("/embeddings") ? "" : "/v1/embeddings");
		try {
			JsonNode root = RestClient.create().post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + model.getApiKey())
					.body(Map.of("model", model.getModelName(), "input", texts)).retrieve().body(JsonNode.class);
			if (root == null || !root.has("data")) throw new IllegalStateException("Embedding 响应缺少 data");
			java.util.ArrayList<float[]> result = new java.util.ArrayList<>();
			for (JsonNode item : root.get("data")) {
				JsonNode vector = item.get("embedding");
				float[] values = new float[vector.size()];
				for (int i = 0; i < values.length; i++) values[i] = (float) vector.get(i).asDouble();
				result.add(values);
			}
			return result;
		} catch (RuntimeException ex) {
			throw new AiModelInvocationException("Embedding 调用失败: " + ex.getMessage(), ex);
		}
	}
}
