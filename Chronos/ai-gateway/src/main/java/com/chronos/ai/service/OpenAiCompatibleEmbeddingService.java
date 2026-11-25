package com.chronos.ai.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.security.SecretEncryptionProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** OpenAI-compatible /v1/embeddings client; works with most cloud providers. */
@Service
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {
	private final AiModelRepository models;
	private final SecretEncryptionProvider encryption;
	private final ObjectMapper mapper;

	public OpenAiCompatibleEmbeddingService(AiModelRepository models, SecretEncryptionProvider encryption,
			ObjectMapper mapper) {
		this.models = models;
		this.encryption = encryption;
		this.mapper = mapper;
	}

	@Override
	public List<float[]> embed(List<String> texts, String modelId) {
		AiModel model = modelId == null || modelId.isBlank()
				? models.findFirstDefaultEmbedding().orElseThrow(() -> new AiModelConfigurationException("未配置默认 Embedding 模型"))
				: models.findById(modelId).orElseThrow(() -> new AiModelConfigurationException("Embedding 模型不存在"));
		if (!"EMBEDDING".equalsIgnoreCase(model.getModelType()) || !Integer.valueOf(1).equals(model.getStatus())) {
			throw new AiModelConfigurationException("Embedding 模型未启用");
		}
		if (model.getApiKeyCiphertext() == null || model.getApiKeyCiphertext().isBlank()) {
			throw new AiModelConfigurationException("Embedding API Key 未配置");
		}
		if (encryption == null) throw new AiModelConfigurationException("平台加密 provider 未配置");
		String apiKey = encryption.decrypt(model.getApiKeyCiphertext());
		String base = model.getBaseUrl();
		if (base == null || base.isBlank()) base = "https://api.openai.com";
		base = AiEndpointSecurity.validate(base);
		String endpoint = embeddingEndpoint(base);
		try {
			String response = RestClient.create().post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + apiKey)
					.body(Map.of("model", model.getModelName(), "input", texts)).retrieve().body(String.class);
			return decodeVectors(mapper.readTree(response), texts.size(), model.getEmbeddingDimension());
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new AiModelInvocationException("Embedding 响应不是有效的 JSON", ex);
		} catch (RuntimeException ex) {
			throw new AiModelInvocationException("Embedding 调用失败，请检查服务地址、模型和向量维度", ex);
		}
	}

	static String embeddingEndpoint(String base) {
		String normalized = base.replaceAll("/+$", "");
		if (normalized.endsWith("/embeddings")) return normalized;
		return normalized + (normalized.endsWith("/v1") ? "/embeddings" : "/v1/embeddings");
	}

	static List<float[]> decodeVectors(JsonNode root, int expectedCount, Integer configuredDimension) {
		JsonNode data = root == null ? null : root.get("data");
		if (data == null || !data.isArray() || data.size() != expectedCount) {
			throw new IllegalStateException("Embedding 返回向量数量与输入不一致");
		}
		float[][] ordered = new float[expectedCount][];
		int dimension = configuredDimension == null ? 0 : configuredDimension;
		for (int position = 0; position < expectedCount; position++) {
			JsonNode item = data.get(position);
			JsonNode index = item.path("index");
			int indexValue = index.isMissingNode() ? position : index.asInt(-1);
			JsonNode vector = item.path("embedding");
			if ((!index.isMissingNode() && (!index.isIntegralNumber() || !index.canConvertToInt())) || indexValue < 0
					|| indexValue >= expectedCount || ordered[indexValue] != null || !vector.isArray() || vector.isEmpty()) {
				throw new IllegalStateException("Embedding 返回了无效的向量或索引");
			}
			if (dimension == 0) dimension = vector.size();
			if (vector.size() != dimension) throw new IllegalStateException("Embedding 向量维度不一致");
			ordered[indexValue] = new float[dimension];
			for (int i = 0; i < dimension; i++) {
				JsonNode coordinate = vector.get(i);
				float value = coordinate.floatValue();
				if (!coordinate.isNumber() || !Float.isFinite(value)) {
					throw new IllegalStateException("Embedding 向量包含无效数值");
				}
				ordered[indexValue][i] = value;
			}
		}
		return java.util.Arrays.asList(ordered);
	}
}
