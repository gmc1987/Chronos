/**
 * 
 */
package com.chronos.service.impl.LLM;

import org.springframework.stereotype.Service;

import com.chronos.commons.eumns.LLMProviderEnum;
import com.chronos.service.factory.LLMServiceStrategy;

/**
 * 
 */
@Service("deepseekService")
public class DeepseekServiceImpl implements LLMServiceStrategy {

	@Override
	public String provider() {
		return LLMProviderEnum.DEEPSEEK.getCode();
	}

	@Override
	public String chat(String message) {
		return null;
	}

}
