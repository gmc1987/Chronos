package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.service.factory.LLMServiceStrategy;

class AiModelChatServiceImplTest {
	private AiModelRepository models;
	private LLMServiceStrategy legacy;
	private DeepSeekChatModelFactory factory;
	private ChatModel chatModel;
	private AiModel model;

	@BeforeEach
	void setUp() {
		models = mock(AiModelRepository.class);
		legacy = mock(LLMServiceStrategy.class);
		factory = mock(DeepSeekChatModelFactory.class);
		chatModel = mock(ChatModel.class);
		model = validModel("model-1");
		when(factory.create(any())).thenReturn(chatModel);
		when(chatModel.call("hello")).thenReturn("world");
	}

	@Test
	void explicitModelUsesCachedClientUntilInvalidated() {
		when(models.findById("model-1")).thenReturn(Optional.of(model));

		AiModelChatServiceImpl service = new AiModelChatServiceImpl(models, legacy, factory);
		assertThat(service.chat("model-1", "hello")).isEqualTo("world");
		assertThat(service.chat("model-1", "hello")).isEqualTo("world");
		assertThat(service.chat("model-1", "hello")).isEqualTo("world");
		verify(factory, times(1)).create(any());
		service.invalidate("model-1");
		service.chat("model-1", "hello");
		verify(factory, times(2)).create(any());
	}

	@Test
	void fallsBackOnlyWhenDatabaseHasNoModelRows() {
		when(models.findFirstDefault()).thenReturn(Optional.empty());
		when(models.count()).thenReturn(0L);
		when(legacy.chat("hello")).thenReturn("legacy");

		assertThat(new AiModelChatServiceImpl(models, legacy, factory).chat(null, "hello"))
				.isEqualTo("legacy");
		verify(factory, times(0)).create(any());
	}

	@Test
	void configuredDefaultIsUsedAndInvalidConfigurationIsNotSilentlyIgnored() {
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		assertThat(new AiModelChatServiceImpl(models, legacy, factory).chat(null, "hello"))
				.isEqualTo("world");

		model.setApiKey(null);
		assertThatThrownBy(() -> new AiModelChatServiceImpl(models, legacy, factory).chat(null, "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("API Key");
	}

	@Test
	void disabledAndUnsupportedModelsFailClearly() {
		model.setStatus(0);
		when(models.findById("model-1")).thenReturn(Optional.of(model));
		assertThatThrownBy(() -> new AiModelChatServiceImpl(models, legacy, factory).chat("model-1", "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("停用");

		model.setStatus(1);
		model.setProvider("openai");
		assertThatThrownBy(() -> new AiModelChatServiceImpl(models, legacy, factory).chat("model-1", "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("不受支持");
	}

	private AiModel validModel(String id) {
		AiModel value = new AiModel();
		value.setId(id);
		value.setModelName("deepseek-chat");
		value.setModelType("chat");
		value.setProvider("deepseek");
		value.setApiKey("secret");
		value.setStatus(1);
		value.setBaseUrl("https://api.deepseek.com");
		return value;
	}
}
