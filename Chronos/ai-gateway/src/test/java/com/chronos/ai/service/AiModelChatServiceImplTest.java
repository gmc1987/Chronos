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
import org.springframework.ai.retry.NonTransientAiException;

import com.chronos.Idao.IDictRepository;
import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.model.pojo.DictItem;
import com.chronos.security.SecretEncryptionProvider;

class AiModelChatServiceImplTest {
	private AiModelRepository models;
	private DeepSeekChatModelFactory factory;
	private ChatModel chatModel;
	private AiModel model;
	private AiModelTypes types;

	@BeforeEach
	void setUp() {
		models = mock(AiModelRepository.class);
		factory = mock(DeepSeekChatModelFactory.class);
		chatModel = mock(ChatModel.class);
		model = validModel("model-1");
		IDictRepository dictionaries = mock(IDictRepository.class);
		DictItem text = new DictItem();
		text.setDictValue("0");
		when(dictionaries.findByDictCode("DICT_MODEL_TEXT")).thenReturn(java.util.List.of(text));
		types = new AiModelTypes(dictionaries);
		when(factory.create(any())).thenReturn(chatModel);
		when(chatModel.call("hello")).thenReturn("world");
	}

	@Test
	void explicitModelUsesCachedClientUntilInvalidated() {
		when(models.findById("model-1")).thenReturn(Optional.of(model));

		AiModelChatServiceImpl service = service();
		assertThat(service.chat("model-1", "hello")).isEqualTo("world");
		assertThat(service.chat("model-1", "hello")).isEqualTo("world");
		assertThat(service.chat("model-1", "hello")).isEqualTo("world");
		verify(factory, times(1)).create(any());
		service.invalidate("model-1");
		service.chat("model-1", "hello");
		verify(factory, times(2)).create(any());
	}

	@Test
	void missingDefaultDoesNotFallBackToYamlConfiguration() {
		when(models.findFirstDefault()).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service().chat(null, "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("模型管理");
		verify(factory, times(0)).create(any());
	}

	@Test
	void configuredDefaultIsUsedAndInvalidConfigurationIsNotSilentlyIgnored() {
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		assertThat(service().chat(null, "hello"))
				.isEqualTo("world");

		model.setApiKey(null);
		assertThatThrownBy(() -> service().chat(null, "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("API Key");
	}

	@Test
	void encryptedTextModelIsAcceptedForDefaultChat() {
		model.setApiKey(null);
		model.setApiKeyCiphertext("encrypted");
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		SecretEncryptionProvider encryption = mock(SecretEncryptionProvider.class);
		when(encryption.decrypt("encrypted")).thenReturn("decrypted");

		assertThat(new AiModelChatServiceImpl(models, factory, encryption, types)
				.chat(null, "hello")).isEqualTo("world");
		verify(encryption).decrypt("encrypted");
	}

	@Test
	void existingProviderNameIsRejectedBeforeCallingTheProvider() {
		model.setModelName("Deepseek");
		when(models.findFirstDefault()).thenReturn(Optional.of(model));

		assertThatThrownBy(() -> service().chat(null, "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("模型管理");
		verify(factory, times(0)).create(any());
	}

	@Test
	void unsupportedProviderModelIdYieldsSafeConfigurationError() {
		model.setModelName("custom-text-model");
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		when(chatModel.call("hello")).thenThrow(new NonTransientAiException(
				"400 - {\"error\":{\"message\":\"The supported API model names are alpha, beta, "
						+ "but you passed invalid. (request_id: example)\","
						+ "\"type\":\"invalid_request_error\"}}"));

		assertThatThrownBy(() -> service().chat(null, "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("Base URL")
				.hasMessageNotContaining("alpha")
				.hasMessageNotContaining("beta")
				.hasMessageNotContaining("request_id");
	}

	@Test
	void unrelatedProviderBadRequestIsNotMisreportedAsInvalidModelId() {
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		NonTransientAiException error = new NonTransientAiException(
				"400 - {\"error\":{\"message\":\"insufficient balance\","
						+ "\"type\":\"invalid_request_error\"}}");
		when(chatModel.call("hello")).thenThrow(error);

		assertThatThrownBy(() -> service().chat(null, "hello")).isSameAs(error);
	}

	@Test
	void disabledAndUnsupportedModelsFailClearly() {
		model.setStatus(0);
		when(models.findById("model-1")).thenReturn(Optional.of(model));
		assertThatThrownBy(() -> service().chat("model-1", "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("停用");

		model.setStatus(1);
		model.setProvider("openai");
		assertThatThrownBy(() -> service().chat("model-1", "hello"))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("不受支持");
	}

	@Test
	void structuredCandidateFactsRejectFreeTextAndUnknownKeys() {
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		var service = service();
		when(chatModel.call("metrics")).thenReturn("{\"factKeys\":[\"BLOCK\",\"UNSCHEDULED\"]}");
		assertThat(service.chatStructured(null, "schedule.candidate.facts.v1", "metrics"))
				.contains("BLOCK");
		when(chatModel.call("metrics")).thenReturn("{\"factKeys\":[\"MADE_UP\"]}");
		assertThatThrownBy(() -> service.chatStructured(null, "schedule.candidate.facts.v1", "metrics"))
				.isInstanceOf(AiStructuredOutputException.class);
		when(chatModel.call("metrics")).thenReturn("{\"factKeys\":[\"BLOCK\"],\"analysis\":\"perfect\"}");
		assertThatThrownBy(() -> service.chatStructured(null, "schedule.candidate.facts.v1", "metrics"))
				.isInstanceOf(AiStructuredOutputException.class);
		when(chatModel.call("metrics")).thenReturn("{\"factKeys\":[\"BLOCK\",\"BLOCK\"]}");
		assertThatThrownBy(() -> service.chatStructured(null, "schedule.candidate.facts.v1", "metrics"))
				.isInstanceOf(AiStructuredOutputException.class);
	}

	@Test
	void structuredSchedulingOutputIsStrictJsonWithBoundedShape() {
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		when(chatModel.call(org.mockito.ArgumentMatchers.contains("张老师周三第3节不能上课")))
				.thenReturn("{\"clauses\":[{\"text\":\"张老师周三第3节不能上课\","
						+ "\"classification\":\"TEACHER_SLOT\"}]}");
		var service = service();
		assertThat(service.chatStructured(null, "schedule.requirement.clauses.v1",
				"张老师周三第3节不能上课")).contains("TEACHER_SLOT");
		when(chatModel.call(org.mockito.ArgumentMatchers.contains("PLC 实训尽量连堂")))
				.thenReturn("{\"clauses\":[{\"text\":\"PLC 实训尽量连堂\","
						+ "\"classification\":\"OFFERING_BLOCK\"}]}");
		assertThat(service.chatStructured(null, "schedule.requirement.clauses.v1",
				"PLC 实训尽量连堂")).contains("OFFERING_BLOCK");
		when(chatModel.call(org.mockito.ArgumentMatchers.contains("PLC 实训仅单周")))
				.thenReturn("{\"clauses\":[{\"text\":\"PLC 实训仅单周\","
						+ "\"classification\":\"WEEK_RULE\"}]}");
		assertThat(service.chatStructured(null, "schedule.requirement.clauses.v1",
				"PLC 实训仅单周")).contains("WEEK_RULE");

		when(chatModel.call(org.mockito.ArgumentMatchers.contains("禁止脚本")))
				.thenReturn("{\"clauses\":[]}");
		assertThatThrownBy(() -> service.chatStructured(null, "schedule.requirement.clauses.v1",
				"禁止脚本")).isInstanceOf(AiStructuredOutputException.class);
		assertThatThrownBy(() -> service.chatStructured(null, "unknown", "需求"))
				.isInstanceOf(AiStructuredOutputException.class);
	}

	private AiModel validModel(String id) {
		AiModel value = new AiModel();
		value.setId(id);
		value.setModelName("deepseek-chat");
		value.setModelType("0");
		value.setProvider("deepseek");
		value.setApiKey("secret");
		value.setStatus(1);
		value.setBaseUrl("https://api.deepseek.com");
		return value;
	}

	private AiModelChatServiceImpl service() {
		return new AiModelChatServiceImpl(models, factory, null, types);
	}
}
