package com.chronos.ai.model;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * Public representation of an AI model configuration.
 *
 * <p>The persisted API key is deliberately not part of this DTO. Only a
 * masked value and a presence flag are exposed so callers can render the
 * configuration state without learning the secret.</p>
 */
@Getter
public class AiModelResponse {
	private final String id;
	private final String createBy;
	private final LocalDateTime createTime;
	private final String lastUpdateBy;
	private final LocalDateTime lastUpdateTime;
	private final String modelName;
	private final String version;
	private final String modelType;
	private final String provider;
	private final String signatureHandler;
	private final String adapterClass;
	private final Integer status;
	private final String maskedApiKey;
	private final boolean hasApiKey;

	private AiModelResponse(AiModel model) {
		this.id = model.getId();
		this.createBy = model.getCreateBy();
		this.createTime = model.getCreateTime();
		this.lastUpdateBy = model.getLastUpdateBy();
		this.lastUpdateTime = model.getLastUpdateTime();
		this.modelName = model.getModelName();
		this.version = model.getVersion();
		this.modelType = model.getModelType();
		this.provider = model.getProvider();
		this.signatureHandler = model.getSignatureHandler();
		this.adapterClass = model.getAdapterClass();
		this.status = model.getStatus();
		this.maskedApiKey = mask(model.getApiKey());
		this.hasApiKey = model.getApiKey() != null && !model.getApiKey().isBlank();
	}

	public static AiModelResponse from(AiModel model) {
		return new AiModelResponse(model);
	}

	private static String mask(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			return null;
		}
		String value = apiKey.trim();
		if (value.length() <= 4) {
			return "****";
		}
		if (value.length() <= 8) {
			return value.substring(0, 1) + "****" + value.substring(value.length() - 1);
		}
		return value.substring(0, 3) + "****" + value.substring(value.length() - 3);
	}
}
