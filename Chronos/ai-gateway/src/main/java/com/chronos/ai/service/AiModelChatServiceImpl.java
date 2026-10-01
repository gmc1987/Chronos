package com.chronos.ai.service;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.client.RestClientException;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.security.SecretEncryptionProvider;

/**
 * Public runtime for all database-backed model calls. The cache is keyed by
 * model id and also checks a configuration fingerprint, so out-of-band
 * database changes cannot keep an old API key or runtime option alive.
 */
@Service
public class AiModelChatServiceImpl implements AiModelChatService {
	private static final String DEEPSEEK_PROVIDER = "deepseek";
	private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
	private static final int DEFAULT_READ_TIMEOUT_MS = 60_000;
	private static final int DEFAULT_CALL_TIMEOUT_MS = 120_000;
	private static final String SCHEDULING_CLAUSES_SCHEMA = "schedule.requirement.clauses.v1";
	private static final String SCHEDULING_FACTS_SCHEMA = "schedule.candidate.facts.v1";

	private final AiModelRepository models;
	private final DeepSeekChatModelFactory modelFactory;
	private final SecretEncryptionProvider encryption;
	private final AiModelTypes modelTypes;
	private final ObjectMapper structuredJson = new ObjectMapper()
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	private final ConcurrentHashMap<String, CachedModel> cache = new ConcurrentHashMap<>();

	@Autowired
	public AiModelChatServiceImpl(
			AiModelRepository models,
			DeepSeekChatModelFactory modelFactory,
			SecretEncryptionProvider encryption,
			AiModelTypes modelTypes) {
		this.models = models;
		this.modelFactory = modelFactory;
		this.encryption = encryption;
		this.modelTypes = modelTypes;
	}

	@Override
	public String chat(String modelId, String message) {
		requireMessage(message);
		boolean explicit = modelId != null && !modelId.isBlank();
		if (explicit) {
			AiModel model = models.findById(modelId.trim())
					.orElseThrow(() -> new AiModelConfigurationException("指定的 AI 模型不存在"));
			return invoke(model, "指定的 AI 模型", message);
		}

		Optional<AiModel> configuredDefault = models.findFirstDefault();
		if (configuredDefault.isPresent()) {
			return invoke(configuredDefault.get(), "默认 AI 模型", message);
		}
		throw new AiModelConfigurationException("未配置默认 AI 模型，请在模型管理中启用并设为默认");
	}

	@Override
	public String chatStructured(String modelId, String schemaId, String message) {
		if (!SCHEDULING_CLAUSES_SCHEMA.equals(schemaId)
				&& !SCHEDULING_FACTS_SCHEMA.equals(schemaId)) {
			throw new AiStructuredOutputException("未知的结构化输出 schema");
		}
		requireMessage(message);
		if (message.length() > 6000) {
			throw new AiStructuredOutputException("结构化模型输入超过长度限制");
		}
		String response = chat(modelId, message);
		if (response == null || response.length() > 16_000) {
			throw new AiStructuredOutputException("模型结构化输出长度无效");
		}
		try {
			JsonNode root = structuredJson.readTree(response);
			if (SCHEDULING_FACTS_SCHEMA.equals(schemaId)) {
				if (root == null || !root.isObject() || root.size() != 1
						|| !root.path("factKeys").isArray()
						|| root.path("factKeys").isEmpty()
						|| root.path("factKeys").size() > 6) {
					throw new AiStructuredOutputException("模型候选指标格式无效");
				}
				java.util.Set<String> seen = new java.util.HashSet<>();
				for (JsonNode item : root.path("factKeys")) {
					if (!item.isTextual() || !java.util.Set.of("SCHEDULED", "UNSCHEDULED",
							"PREFERRED_SLOT", "BLOCK", "CAMPUS_SWITCH", "TEACHER_GAP")
							.contains(item.asText()) || !seen.add(item.asText())) {
						throw new AiStructuredOutputException("模型候选指标代码无效");
					}
				}
				return root.toString();
			}
			if (root == null || !root.isObject() || root.size() != 1 || !root.has("clauses")
					|| !root.get("clauses").isArray()
					|| root.get("clauses").isEmpty() || root.get("clauses").size() > 30) {
				throw new AiStructuredOutputException("模型未返回合法的子句列表");
			}
			for (JsonNode clause : root.get("clauses")) {
				if (!clause.isObject() || clause.size() != ("SLOT_RULE".equals(
						clause.path("classification").asText()) ? 3 : 2)
						|| !clause.path("text").isTextual()
						|| clause.path("text").asText().isBlank()
						|| clause.path("text").asText().length() > 2000
						|| !clause.path("classification").isTextual()
						|| !java.util.Set.of("TEACHER_SLOT", "OFFERING_BLOCK", "WEEK_RULE",
								"LOCK_ENTRY", "TEACHER_PRIORITY", "GENERATION", "UNSUPPORTED",
								"SLOT_RULE")
								.contains(clause.path("classification").asText())) {
					throw new AiStructuredOutputException("模型子句格式或分类无效");
				}
				if ("SLOT_RULE".equals(clause.path("classification").asText())) {
					JsonNode rule = clause.path("rule");
					if (!rule.isObject() || rule.size() != 5
							|| !rule.path("subject").isTextual()
							|| !java.util.Set.of("TEACHER", "OFFERING", "ALL_TEACHERS")
									.contains(rule.path("subject").asText())
							|| !rule.path("reference").isTextual()
							|| rule.path("reference").asText().length() > 128
							|| !rule.path("action").isTextual()
							|| !"FORBID".equals(rule.path("action").asText())
							|| !rule.path("days").isArray() || rule.path("days").isEmpty()
							|| rule.path("days").size() > 7
							|| !rule.path("periods").isArray() || rule.path("periods").isEmpty()
							|| rule.path("periods").size() > 20) {
						throw new AiStructuredOutputException("模型组合时段规则格式无效");
					}
					for (JsonNode day : rule.path("days")) {
						if (!day.canConvertToInt() || day.asInt() < 1 || day.asInt() > 7) {
							throw new AiStructuredOutputException("模型星期无效");
						}
					}
					for (JsonNode period : rule.path("periods")) {
						if (!period.canConvertToInt() || period.asInt() < 1 || period.asInt() > 20) {
							throw new AiStructuredOutputException("模型节次无效");
						}
					}
				}
			}
			return root.toString();
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new AiStructuredOutputException("模型未返回有效 JSON", exception);
		}
	}

	@Override
	public void invalidate(String modelId) {
		if (modelId != null && !modelId.isBlank()) {
			cache.remove(modelId.trim());
		}
	}

	private String invoke(AiModel model, String label, String message) {
		validate(model, label);
		String modelId = model.getId();
		if (modelId == null || modelId.isBlank()) {
			throw new AiModelConfigurationException(label + "缺少模型 ID");
		}
		ChatModel chatModel = cachedModel(model).chatModel();
		try {
			return chatModel.call(message);
		} catch (NonTransientAiException exception) {
			if (AiModelNameValidation.isUnsupportedModel(exception)) {
				throw AiModelNameValidation.unsupportedModel();
			}
			throw exception;
		} catch (RestClientException | WebClientException | IllegalStateException exception) {
			throw new AiModelInvocationException(label + "调用失败，请稍后重试", exception);
		}
	}

	private CachedModel cachedModel(AiModel model) {
		String id = model.getId().trim();
		String fingerprint = fingerprint(model);
		return cache.compute(id, (key, current) -> current != null && current.fingerprint().equals(fingerprint)
				? current
				: new CachedModel(fingerprint, modelFactory.create(model)));
	}

	private void validate(AiModel model, String label) {
		if (!Integer.valueOf(1).equals(model.getStatus())) {
			throw new AiModelConfigurationException(label + "已停用");
		}
		if (!modelTypes.isText(model.getModelType())) {
			throw new AiModelConfigurationException(label + "不是文本模型");
		}
		AiModelNameValidation.validate(model);
		if (model.getApiKey() == null || model.getApiKey().isBlank()) {
			if (model.getApiKeyCiphertext() == null || model.getApiKeyCiphertext().isBlank()) {
				throw new AiModelConfigurationException(label + "未配置 API Key");
			}
			if (encryption == null) {
				throw new AiModelConfigurationException(label + "平台加密 provider 未配置");
			}
			model.setApiKey(encryption.decrypt(model.getApiKeyCiphertext()));
		}
		if (!DEEPSEEK_PROVIDER.equals(normalize(model.getProvider()))) {
			throw new AiModelConfigurationException(label + "供应商不受支持，当前仅支持 DeepSeek");
		}
		if (model.getModelName() == null || model.getModelName().isBlank()) {
			throw new AiModelConfigurationException(label + "模型名称不能为空");
		}
		if (model.getBaseUrl() == null || model.getBaseUrl().isBlank()) {
			throw new AiModelConfigurationException(label + "Base URL 不能为空");
		}
		try {
			AiEndpointSecurity.validate(model.getBaseUrl().trim());
		} catch (AiModelConfigurationException exception) {
			throw new AiModelConfigurationException(label + " Base URL 必须是可信的 HTTPS 公网地址");
		}
		validateRange(model.getConnectTimeoutMs(), 100, 120_000, label + "连接超时");
		validateRange(model.getReadTimeoutMs(), 100, 600_000, label + "读取超时");
		validateRange(model.getCallTimeoutMs(), 100, 600_000, label + "调用超时");
		if (model.getTemperature() != null
				&& (model.getTemperature() < 0 || model.getTemperature() > 2)) {
			throw new AiModelConfigurationException(label + "temperature 超出范围");
		}
		if (model.getTopP() != null && (model.getTopP() < 0 || model.getTopP() > 1)) {
			throw new AiModelConfigurationException(label + "topP 超出范围");
		}
		if (model.getMaxTokens() != null && (model.getMaxTokens() < 1 || model.getMaxTokens() > 100_000)) {
			throw new AiModelConfigurationException(label + "maxTokens 超出范围");
		}
	}

	private void validateRange(Integer value, int min, int max, String label) {
		if (value != null && (value < min || value > max)) {
			throw new AiModelConfigurationException(label + "超出范围");
		}
	}

	private String fingerprint(AiModel model) {
		return String.join("|",
				value(model.getApiKey()),
				value(model.getModelName()),
				value(model.getBaseUrl()),
				value(model.getConnectTimeoutMs(), DEFAULT_CONNECT_TIMEOUT_MS),
				value(model.getReadTimeoutMs(), DEFAULT_READ_TIMEOUT_MS),
				value(model.getCallTimeoutMs(), DEFAULT_CALL_TIMEOUT_MS),
				value(model.getTemperature()),
				value(model.getMaxTokens()),
				value(model.getTopP()));
	}

	private String value(Object value) {
		return value == null ? "" : value.toString();
	}

	private String value(Integer value, int defaultValue) {
		return value == null ? Integer.toString(defaultValue) : value.toString();
	}

	private void requireMessage(String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("AI 输入内容不能为空");
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private record CachedModel(String fingerprint, ChatModel chatModel) {
	}
}
