package com.chronos.ai.service;

import java.time.Duration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.chronos.ai.model.AiModel;

/** Creates a fully configured, database-backed DeepSeek client. */
@Component
public class DeepSeekChatModelFactory {
	/**
	 * 管理后台的模型配置允许较长超时，但同步交互入口不能无限继承该值。
	 * 超过一分钟后由上层智能体切换到确定性规划器，避免页面长时间停在“发送中”。
	 */
	static final int MAX_INTERACTIVE_READ_TIMEOUT_MS = 60_000;

	public ChatModel create(AiModel model) {
		int connectTimeout = model.getConnectTimeoutMs() == null ? 10_000 : model.getConnectTimeoutMs();
		int callTimeout = model.getCallTimeoutMs() == null ? 120_000 : model.getCallTimeoutMs();
		int readTimeout = effectiveReadTimeoutMs(model, callTimeout);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

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
				// The agent owns its recovery strategy. Provider retries used to turn a
				// 60-second read timeout into a two-to-three minute opaque wait.
				.retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
				.build();
	}

	static int effectiveReadTimeoutMs(AiModel model, int callTimeout) {
		int configuredReadTimeout = model.getReadTimeoutMs() == null
				? MAX_INTERACTIVE_READ_TIMEOUT_MS
				: model.getReadTimeoutMs();
		return Math.min(configuredReadTimeout,
				Math.min(callTimeout, MAX_INTERACTIVE_READ_TIMEOUT_MS));
	}
}
