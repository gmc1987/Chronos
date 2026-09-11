package com.chronos.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.service.factory.LLMServiceStrategy;

/** 提供不包含密钥、地址等敏感配置的模型就绪状态。 */
@RestController
public class AIChatController {
	private final LLMServiceStrategy llmService;

	public AIChatController(LLMServiceStrategy llmService) {
		this.llmService = llmService;
	}

	@GetMapping("/ai/model/status")
	public ResultData<Map<String, Object>> status() {
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("provider", llmService.provider());
		status.put("available", llmService.available());
		status.put(
				"message",
				llmService.available()
						? "AI 模型已就绪"
						: "AI 模型尚未配置，请由管理员设置模型类型和有效密钥");
		return ResultData.<Map<String, Object>>builder()
				.code("200")
				.msg("ok")
				.data(status)
				.build();
	}
}
