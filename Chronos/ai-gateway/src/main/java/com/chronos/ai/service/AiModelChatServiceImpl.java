package com.chronos.ai.service;

import java.util.Locale;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.service.factory.LLMServiceStrategy;

/**
 * Resolves persisted model selection without exposing the AI entity to
 * knowledge-center. Phase one deliberately supports only DeepSeek.
 */
@Service
public class AiModelChatServiceImpl implements AiModelChatService {
	private static final String DEEPSEEK_PROVIDER = "deepseek";

	private final AiModelRepository models;
	private final LLMServiceStrategy legacyDeepseek;

	public AiModelChatServiceImpl(
			AiModelRepository models,
			@Qualifier("deepseekService") LLMServiceStrategy legacyDeepseek) {
		this.models = models;
		this.legacyDeepseek = legacyDeepseek;
	}

	@Override
	public String chat(String modelId, String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("AI 输入内容不能为空");
		}
		AiModel model;
		boolean explicit = modelId != null && !modelId.isBlank();
		if (explicit) {
			model = models.findById(modelId.trim())
					.orElseThrow(() -> new IllegalArgumentException("指定的 AI 模型不存在"));
		} else {
			model = models.findFirstDefault().orElse(null);
		}
		if (model == null) {
			return legacyDeepseek.chat(message);
		}
		validate(model, explicit ? "指定的 AI 模型" : "默认 AI 模型");
		return deepseek(model, message);
	}

	private void validate(AiModel model, String label) {
		if (!Integer.valueOf(1).equals(model.getStatus())) {
			throw new IllegalStateException(label + "已停用");
		}
		if (model.getApiKey() == null || model.getApiKey().isBlank()) {
			throw new IllegalStateException(label + "未配置 API Key");
		}
		if (!DEEPSEEK_PROVIDER.equals(normalize(model.getProvider()))) {
			throw new IllegalStateException(label + "供应商不受支持，当前仅支持 DeepSeek");
		}
		if (model.getModelName() == null || model.getModelName().isBlank()) {
			throw new IllegalStateException(label + "模型名称不能为空");
		}
	}

	private String deepseek(AiModel model, String message) {
		DeepSeekApi api = DeepSeekApi.builder()
				.apiKey(model.getApiKey().trim())
				.build();
		DeepSeekChatOptions options = DeepSeekChatOptions.builder()
				.model(model.getModelName().trim())
				.build();
		ChatModel chatModel = DeepSeekChatModel.builder()
				.deepSeekApi(api)
				.options(options)
				.build();
		return chatModel.call(message);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
