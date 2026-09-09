/**
 * 
 */
package com.chronos.service.impl.LLM;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.chronos.commons.eumns.LLMProviderEnum;
import com.chronos.service.factory.LLMServiceStrategy;

/** DeepSeek 的统一模型适配器，业务模块不得直接依赖厂商 SDK。 */
@Service("deepseekService")
public class DeepseekServiceImpl implements LLMServiceStrategy {
	private final ChatModel chatModel;

	public DeepseekServiceImpl(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String provider() {
		return LLMProviderEnum.DEEPSEEK.getCode();
	}

	@Override
	public String chat(String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("AI 输入内容不能为空");
		}
		return chatModel.call(message);
	}

}
