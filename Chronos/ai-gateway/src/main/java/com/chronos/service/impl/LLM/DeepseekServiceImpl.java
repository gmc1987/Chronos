/**
 * 
 */
package com.chronos.service.impl.LLM;

import org.springframework.stereotype.Service;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.ai.service.AiModelChatService;
import com.chronos.commons.eumns.LLMProviderEnum;
import com.chronos.service.factory.LLMServiceStrategy;

/** DeepSeek 的统一模型适配器，业务模块不得直接依赖厂商 SDK。 */
@Service("deepseekService")
public class DeepseekServiceImpl implements LLMServiceStrategy {
	private final AiModelRepository models;
	private final AiModelChatService runtime;

	public DeepseekServiceImpl(AiModelRepository models, AiModelChatService runtime) {
		this.models = models;
		this.runtime = runtime;
	}

	@Override
	public String provider() {
		return LLMProviderEnum.DEEPSEEK.getCode();
	}

	@Override
	public boolean available() {
		return models.findFirstDefault()
				.filter(this::isUsable)
				.isPresent();
	}

	@Override
	public String chat(String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("AI 输入内容不能为空");
		}
		// 旧接口也统一走模型管理中的默认模型，避免再次读取 YAML 中的密钥。
		return runtime.chat(null, message);
	}

	private boolean isUsable(AiModel model) {
		return Integer.valueOf(1).equals(model.getStatus())
				&& "deepseek".equalsIgnoreCase(model.getProvider())
				&& "CHAT".equalsIgnoreCase(model.getModelType())
				&& model.getApiKey() != null
				&& !model.getApiKey().isBlank()
				&& model.getModelName() != null
				&& !model.getModelName().isBlank();
	}
}
