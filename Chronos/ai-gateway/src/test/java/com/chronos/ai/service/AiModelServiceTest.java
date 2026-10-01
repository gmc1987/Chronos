package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.security.SecretEncryptionProvider;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.ai.model.AiModelResponse;
import com.chronos.Idao.IDictRepository;
import com.chronos.model.pojo.DictItem;

class AiModelServiceTest {
	@Test
	void updateInvalidatesRuntimeAfterChangingConfiguration() {
		AiModelRepository repository = mock(AiModelRepository.class);
		AiModelChatService runtime = mock(AiModelChatService.class);
		SecretEncryptionProvider encryption = mock(SecretEncryptionProvider.class);
		IAuditLogService audit = mock(IAuditLogService.class);
		AiModel existing = model("model-1");
		AiModel command = model("model-1");
		command.setModelName("deepseek-reasoner");
		when(repository.findById("model-1")).thenReturn(Optional.of(existing));
		when(repository.save(any(AiModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(encryption.encrypt("secret")).thenReturn("v1:ciphertext");
		when(encryption.keyVersion()).thenReturn("v1");
		when(encryption.fingerprint("secret")).thenReturn("fingerprint");

		new AiModelService(repository, runtime, encryption, audit, textTypes()).update(command);

		verify(runtime).invalidate("model-1");
		verify(audit).log("system", "AI_MODEL_UPDATE", "modelId=model-1,provider=deepseek,keyVersion=v1,keyFingerprint=fingerprint");
	}

	@Test
	void responseNeverContainsCredentialOrPartialFingerprint() {
		AiModel model = model("model-1");
		model.setApiKey(null);
		model.setApiKeyCiphertext("v1:encrypted");
		model.setApiKeyFingerprint("secret-fingerprint");

		AiModelResponse response = AiModelResponse.from(model);

		org.assertj.core.api.Assertions.assertThat(response.getMaskedApiKey()).isEqualTo("********");
		org.assertj.core.api.Assertions.assertThat(response.isHasApiKey()).isTrue();
		org.assertj.core.api.Assertions.assertThat(response.toString()).doesNotContain("secret");
	}

	@Test
	void listRejectsUnboundedPageSize() {
		AiModelService service = new AiModelService(mock(AiModelRepository.class),
				mock(AiModelChatService.class), mock(SecretEncryptionProvider.class),
				mock(IAuditLogService.class), textTypes());

		assertThatThrownBy(() -> service.list("", "", null, 0, 101))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("每页数量");
	}

	@Test
	void existingTextModelCanBeSetAsDefaultWithoutChangingItsDictionaryType() {
		AiModelRepository repository = mock(AiModelRepository.class);
		AiModel existing = model("model-1");
		existing.setApiKey(null);
		existing.setApiKeyCiphertext("encrypted");
		AiModel command = model("model-1");
		command.setApiKey(null);
		command.setIsDefault(true);
		when(repository.findById("model-1")).thenReturn(Optional.of(existing));
		when(repository.save(any(AiModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AiModel result = new AiModelService(repository, mock(AiModelChatService.class),
				mock(SecretEncryptionProvider.class), mock(IAuditLogService.class), textTypes())
				.update(command);

		assertThat(result.getModelType()).isEqualTo("0");
		assertThat(result.getIsDefault()).isTrue();
		verify(repository).clearDefaultsExcept("model-1");
	}

	@Test
	void refusesProviderDisplayNameAsDeepSeekModelIdOnCreateAndUpdate() {
		AiModelRepository repository = mock(AiModelRepository.class);
		AiModel existing = model("model-1");
		existing.setApiKey(null);
		existing.setApiKeyCiphertext("encrypted");
		when(repository.findById("model-1")).thenReturn(Optional.of(existing));
		AiModelService service = new AiModelService(repository, mock(AiModelChatService.class),
				mock(SecretEncryptionProvider.class), mock(IAuditLogService.class), textTypes());
		AiModel command = model("model-1");
		command.setModelName("Deepseek");

		assertThatThrownBy(() -> service.update(command))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("API 模型标识");
		assertThatThrownBy(() -> service.create(command))
				.isInstanceOf(AiModelConfigurationException.class)
				.hasMessageContaining("API 模型标识");
		org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
	}

	private AiModel model(String id) {
		AiModel value = new AiModel();
		value.setId(id);
		value.setModelName("deepseek-chat");
		value.setModelType("0");
		value.setProvider("deepseek");
		value.setApiKey("secret");
		value.setStatus(1);
		value.setIsDefault(false);
		return value;
	}

	private AiModelTypes textTypes() {
		IDictRepository dictionaries = mock(IDictRepository.class);
		DictItem text = new DictItem();
		text.setDictValue("0");
		when(dictionaries.findByDictCode("DICT_MODEL_TEXT")).thenReturn(java.util.List.of(text));
		return new AiModelTypes(dictionaries);
	}
}
