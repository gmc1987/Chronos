package com.chronos.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.commons.model.ResultData;
import com.chronos.service.factory.LLMServiceStrategy;

/** 提供不包含密钥、地址等敏感配置的模型就绪状态。 */
@RestController
public class AIChatController {
	private final LLMServiceStrategy llmService;
	private final AiModelRepository models;

	public AIChatController(
			LLMServiceStrategy llmService,
			AiModelRepository models) {
		this.llmService = llmService;
		this.models = models;
	}

	@GetMapping("/ai/model/status")
	public ResultData<Map<String, Object>> status() {
		AiModel configuredModel = models.findFirstDefault()
				.filter(this::isAvailable)
				.orElse(null);
		boolean available = configuredModel != null || llmService.available();
		String provider = configuredModel == null
				? llmService.provider()
				: configuredModel.getProvider();
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("provider", provider);
		status.put("available", available);
		status.put(
				"message",
				available
						? "AI 模型已就绪"
						: "AI 模型尚未配置，请由管理员设置模型类型和有效密钥");
		return ResultData.<Map<String, Object>>builder()
				.code("200")
				.msg("ok")
				.data(status)
				.build();
	}

	/**
	 * 状态接口只判断模型能否被网关调用，不返回模型 ID、密钥或服务地址。
	 */
	private boolean isAvailable(AiModel model) {
		return Integer.valueOf(1).equals(model.getStatus())
				&& model.getApiKey() != null
				&& !model.getApiKey().isBlank()
				&& model.getModelName() != null
				&& !model.getModelName().isBlank()
				&& model.getProvider() != null
				&& !model.getProvider().isBlank();
	}
}
