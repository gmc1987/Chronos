package com.chronos.ai.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;

/** AI 模型配置管理，不负责模型调用或供应商连接。 */
@Service
public class AiModelService {
	private final AiModelRepository models;

	public AiModelService(AiModelRepository models) {
		this.models = models;
	}

	@Transactional(readOnly = true)
	public Page<AiModel> list(String modelName, String provider, Integer status, int page, int size) {
		return models.search(
				normalize(modelName),
				normalize(provider),
				status,
				PageRequest.of(Math.max(0, page), safeSize(size)));
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
		return models.save(target);
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
		return models.save(target);
	}

	@Transactional
	public void delete(String id) {
		if (!models.existsById(id)) {
			throw new IllegalArgumentException("AI 模型不存在");
		}
		models.deleteById(id);
	}

	private AiModel normalize(AiModel source, AiModel target, boolean creating) {
		if (source == null) {
			throw new IllegalArgumentException("AI 模型配置不能为空");
		}
		target.setModelName(required(source.getModelName(), "模型名称不能为空"));
		target.setVersion(trimToNull(source.getVersion()));
		target.setModelType(required(source.getModelType(), "模型类型不能为空"));
		target.setProvider(required(source.getProvider(), "供应商不能为空"));
		String apiKey = trimToNull(source.getApiKey());
		if (creating) {
			target.setApiKey(requiredApiKey(apiKey));
		} else if (apiKey != null && !isMaskedApiKey(apiKey)) {
			target.setApiKey(apiKey);
		}
		target.setSignatureHandler(trimToNull(source.getSignatureHandler()));
		target.setAdapterClass(trimToNull(source.getAdapterClass()));
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

	private int safeSize(int size) {
		return Math.min(Math.max(1, size), 100);
	}
}
