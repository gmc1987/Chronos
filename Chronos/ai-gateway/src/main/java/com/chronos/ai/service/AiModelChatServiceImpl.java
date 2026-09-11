package com.chronos.ai.service;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.client.RestClientException;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.service.factory.LLMServiceStrategy;

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

	private final AiModelRepository models;
	private final LLMServiceStrategy legacyDeepseek;
	private final DeepSeekChatModelFactory modelFactory;
	private final ConcurrentHashMap<String, CachedModel> cache = new ConcurrentHashMap<>();

	@Autowired
	public AiModelChatServiceImpl(
			AiModelRepository models,
			@Qualifier("deepseekService") LLMServiceStrategy legacyDeepseek,
			DeepSeekChatModelFactory modelFactory) {
		this.models = models;
		this.legacyDeepseek = legacyDeepseek;
		this.modelFactory = modelFactory;
	}

	/** Kept for small module tests and source-compatible callers. */
	public AiModelChatServiceImpl(
			AiModelRepository models,
			@Qualifier("deepseekService") LLMServiceStrategy legacyDeepseek) {
		this(models, legacyDeepseek, new DeepSeekChatModelFactory());
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
		if (models.count() == 0) {
			return invokeLegacy(message);
		}
		throw new AiModelConfigurationException("未配置默认 AI 模型");
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
		} catch (RestClientException | WebClientException | IllegalStateException exception) {
			throw new AiModelInvocationException(label + "调用失败，请稍后重试", exception);
		}
	}

	private String invokeLegacy(String message) {
		try {
			return legacyDeepseek.chat(message);
		} catch (RestClientException | WebClientException | IllegalStateException exception) {
			throw new AiModelInvocationException("旧版 DeepSeek 模型调用失败，请稍后重试", exception);
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
		if (model.getApiKey() == null || model.getApiKey().isBlank()) {
			throw new AiModelConfigurationException(label + "未配置 API Key");
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
			URI baseUrl = URI.create(model.getBaseUrl().trim());
			if ((!"http".equalsIgnoreCase(baseUrl.getScheme())
					&& !"https".equalsIgnoreCase(baseUrl.getScheme()))
					|| baseUrl.getHost() == null) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException exception) {
			throw new AiModelConfigurationException(label + "Base URL 必须是有效的 HTTP(S) 地址");
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
