package com.chronos.ai.service;

import java.time.Duration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.chronos.ai.model.AiModel;

/** Creates a fully configured, database-backed DeepSeek client. */
@Component
public class DeepSeekChatModelFactory {
	public ChatModel create(AiModel model) {
		int connectTimeout = model.getConnectTimeoutMs() == null ? 10_000 : model.getConnectTimeoutMs();
		int readTimeout = model.getReadTimeoutMs() == null ? 60_000 : model.getReadTimeoutMs();
		int callTimeout = model.getCallTimeoutMs() == null ? 120_000 : model.getCallTimeoutMs();
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
		requestFactory.setReadTimeout(Duration.ofMillis(Math.min(readTimeout, callTimeout)));

		DeepSeekApi api = DeepSeekApi.builder()
				.apiKey(model.getApiKey().trim())
				.baseUrl(model.getBaseUrl())
				.restClientBuilder(RestClient.builder().requestFactory(requestFactory))
				.build();
		DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder()
				.model(model.getModelName().trim());
		if (model.getTemperature() != null) {
			options.temperature(model.getTemperature());
		}
		if (model.getMaxTokens() != null) {
			options.maxTokens(model.getMaxTokens());
		}
		if (model.getTopP() != null) {
			options.topP(model.getTopP());
		}
		return DeepSeekChatModel.builder()
				.deepSeekApi(api)
				.options(options.build())
				.build();
	}
}
