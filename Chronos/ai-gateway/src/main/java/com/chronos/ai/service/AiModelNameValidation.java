package com.chronos.ai.service;

import com.chronos.ai.model.AiModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.retry.NonTransientAiException;

final class AiModelNameValidation {
	private static final String MESSAGE = "模型名称应填写与 Base URL 对应的 API 模型标识，而不是供应商名称；请在模型管理中核对";
	private static final ObjectMapper JSON = new ObjectMapper();

	private AiModelNameValidation() {
	}

	static void validate(AiModel model) {
		if ("deepseek".equalsIgnoreCase(model.getProvider())
				&& model.getModelName() != null
				&& model.getModelName().trim().equalsIgnoreCase(model.getProvider())) {
			throw new AiModelConfigurationException(MESSAGE);
		}
	}

	static boolean isUnsupportedModel(NonTransientAiException exception) {
		String response = exception.getMessage();
		if (response == null || !response.startsWith("400 - ")) {
			return false;
		}
		try {
			JsonNode error = JSON.readTree(response.substring("400 - ".length())).path("error");
			String message = error.path("message").asText("");
			return "invalid_request_error".equals(error.path("type").asText())
					&& message.contains("supported API model names")
					&& message.contains("but you passed");
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception2) {
			return false;
		}
	}

	static AiModelConfigurationException unsupportedModel() {
		return new AiModelConfigurationException("提供方不支持当前模型名称；请在模型管理中核对 Base URL 和 API 模型标识");
	}
}
