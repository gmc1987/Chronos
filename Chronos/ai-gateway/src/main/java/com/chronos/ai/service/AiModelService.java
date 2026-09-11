package com.chronos.ai.service;

import java.net.URI;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;

/** AI 模型配置管理，不负责模型调用或供应商连接。 */
@Service
public class AiModelService {
	private final AiModelRepository models;
	private final AiModelChatService runtime;

	public AiModelService(AiModelRepository models) {
		this(models, null);
	}

	@Autowired
	public AiModelService(AiModelRepository models, AiModelChatService runtime) {
		this.models = models;
		this.runtime = runtime;
	}

	@Transactional(readOnly = true)
	public Page<AiModel> list(String modelName, String provider, Integer status, int page, int size) {
		if (page < 0 || page > 100_000) {
			throw new IllegalArgumentException("页码超出范围");
		}
		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
		}
		return models.search(
				normalize(modelName),
				normalize(provider),
				status,
				PageRequest.of(page, size));
	}

	@Transactional(readOnly = true)
	public AiModel get(String id) {
		return models.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("AI 模型不存在"));
	}

	@Transactional
	public AiModel create(AiModel command) {
		AiModel target = normalize(command, new AiModel(), true);
		applyDefault(target, Boolean.TRUE.equals(target.getIsDefault()), null);
		if (Boolean.TRUE.equals(target.getEmbeddingDefault())) models.clearEmbeddingDefaults();
		AiModel saved = models.save(target);
		invalidate(saved.getId());
		return saved;
	}

	@Transactional
	public AiModel update(AiModel command) {
		if (command.getId() == null || command.getId().isBlank()) {
			throw new IllegalArgumentException("AI 模型 ID 不能为空");
		}
		AiModel target = get(command.getId());
		boolean explicitlyDefault = Boolean.TRUE.equals(command.getIsDefault());
		boolean requestedDefault = command.getIsDefault() == null
				? Boolean.TRUE.equals(target.getIsDefault())
				: Boolean.TRUE.equals(command.getIsDefault());
		normalize(command, target, false);
		if (explicitlyDefault && !Integer.valueOf(1).equals(target.getStatus())) {
			throw new IllegalArgumentException("只有启用的模型才能设为默认模型");
		}
		if (!Integer.valueOf(1).equals(target.getStatus())) {
			requestedDefault = false;
		}
		applyDefault(target, requestedDefault, target.getId());
		if (Boolean.TRUE.equals(target.getEmbeddingDefault())) models.clearEmbeddingDefaults();
		AiModel saved = models.save(target);
		invalidate(saved.getId());
		return saved;
	}

	@Transactional
	public void delete(String id) {
		if (!models.existsById(id)) {
			throw new IllegalArgumentException("AI 模型不存在");
		}
		models.deleteById(id);
		invalidate(id);
	}

	private AiModel normalize(AiModel source, AiModel target, boolean creating) {
		if (source == null) {
			throw new IllegalArgumentException("AI 模型配置不能为空");
		}
		target.setModelName(required(source.getModelName(), "模型名称不能为空"));
		target.setVersion(trimToNull(source.getVersion()));
		target.setModelType(required(source.getModelType(), "模型类型不能为空"));
		target.setProvider(required(source.getProvider(), "供应商不能为空").toLowerCase(Locale.ROOT));
		target.setModelType(target.getModelType().toUpperCase(Locale.ROOT));
		String apiKey = trimToNull(source.getApiKey());
		if (creating) {
			target.setApiKey(requiredApiKey(apiKey));
		} else if (apiKey != null && !isMaskedApiKey(apiKey)) {
			target.setApiKey(apiKey);
		}
		target.setSignatureHandler(trimToNull(source.getSignatureHandler()));
		target.setAdapterClass(trimToNull(source.getAdapterClass()));
		if (creating || source.getBaseUrl() != null) {
			target.setBaseUrl(normalizeBaseUrl(source.getBaseUrl()));
		} else if (target.getBaseUrl() == null || target.getBaseUrl().isBlank()) {
			target.setBaseUrl("https://api.deepseek.com");
		}
		target.setConnectTimeoutMs(normalizeTimeout(
				source.getConnectTimeoutMs(), creating ? Integer.valueOf(10_000) : target.getConnectTimeoutMs(), "连接超时"));
		target.setReadTimeoutMs(normalizeTimeout(
				source.getReadTimeoutMs(), creating ? Integer.valueOf(60_000) : target.getReadTimeoutMs(), "读取超时"));
		target.setCallTimeoutMs(normalizeTimeout(
				source.getCallTimeoutMs(), creating ? Integer.valueOf(120_000) : target.getCallTimeoutMs(), "调用超时"));
		if (creating || source.getTemperature() != null) {
			target.setTemperature(source.getTemperature());
		}
		if (creating || source.getMaxTokens() != null) {
			target.setMaxTokens(source.getMaxTokens());
		}
		if (creating || source.getTopP() != null) {
			target.setTopP(source.getTopP());
		}
		if (creating || source.getEmbeddingDimension() != null) {
			target.setEmbeddingDimension(source.getEmbeddingDimension());
		}
		if (creating || source.getEmbeddingDefault() != null) {
			target.setEmbeddingDefault(Boolean.TRUE.equals(source.getEmbeddingDefault()));
		}
		validateOptions(target);
		Integer status = source.getStatus() == null ? 1 : source.getStatus();
		if (status != 0 && status != 1) {
			throw new IllegalArgumentException("模型状态只能是启用或禁用");
		}
		target.setStatus(status);
		if (creating || source.getIsDefault() != null) {
			target.setIsDefault(Boolean.TRUE.equals(source.getIsDefault()));
		}
		return target;
	}

	private void applyDefault(AiModel target, boolean requested, String existingId) {
		if (!requested) {
			target.setIsDefault(false);
			return;
		}
		if (!Integer.valueOf(1).equals(target.getStatus())) {
			throw new IllegalArgumentException("只有启用的模型才能设为默认模型");
		}
		if (target.getApiKey() == null || target.getApiKey().isBlank()) {
			throw new IllegalArgumentException("只有已配置 API Key 的模型才能设为默认模型");
		}
		if (existingId == null) {
			models.clearDefaults();
		} else {
			models.clearDefaultsExcept(existingId);
		}
		target.setIsDefault(true);
	}

	private String requiredApiKey(String value) {
		if (value == null || isMaskedApiKey(value)) {
			throw new IllegalArgumentException("API Key 不能为空");
		}
		return value;
	}

	private boolean isMaskedApiKey(String value) {
		return value.indexOf('*') >= 0;
	}

	private String required(String value, String message) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			throw new IllegalArgumentException(message);
		}
		return normalized;
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String normalizeBaseUrl(String value) {
		String baseUrl = value == null || value.isBlank()
				? "https://api.deepseek.com"
				: value.trim();
		try {
			URI uri = URI.create(baseUrl);
			if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())
					|| uri.getHost() == null) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException exception) {
			throw new AiModelConfigurationException("Base URL 必须是有效的 HTTP(S) 地址");
		}
		return baseUrl;
	}

	private Integer normalizeTimeout(Integer value, Integer current, String label) {
		int result = value == null ? (current == null ? defaultTimeout(label) : current) : value;
		int max = "连接超时".equals(label) ? 120_000 : 600_000;
		if (result < 100 || result > max) {
			throw new AiModelConfigurationException(label + "必须在 100 到 " + max + " 毫秒之间");
		}
		return result;
	}

	private int defaultTimeout(String label) {
		return "连接超时".equals(label) ? 10_000 : "读取超时".equals(label) ? 60_000 : 120_000;
	}

	private void validateOptions(AiModel target) {
		if (target.getTemperature() != null
				&& (target.getTemperature() < 0 || target.getTemperature() > 2)) {
			throw new AiModelConfigurationException("temperature 必须在 0 到 2 之间");
		}
		if (target.getTopP() != null && (target.getTopP() < 0 || target.getTopP() > 1)) {
			throw new AiModelConfigurationException("topP 必须在 0 到 1 之间");
		}
		if (target.getMaxTokens() != null
				&& (target.getMaxTokens() < 1 || target.getMaxTokens() > 100_000)) {
			throw new AiModelConfigurationException("maxTokens 必须在 1 到 100000 之间");
		}
	}

	private void invalidate(String id) {
		if (runtime != null) {
			runtime.invalidate(id);
		}
	}
}
