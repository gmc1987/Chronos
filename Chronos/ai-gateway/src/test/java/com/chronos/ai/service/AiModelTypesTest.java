package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IDictRepository;
import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.model.pojo.DictItem;
import com.chronos.service.factory.LLMServiceStrategy;
import com.chronos.service.impl.LLM.DeepseekServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiModelTypesTest {
	@Test
	void availabilityUsesCurrentDictionaryValueAndEncryptedCredential() {
		IDictRepository dictionaries = mock(IDictRepository.class);
		DictItem text = new DictItem();
		text.setDictValue("0");
		when(dictionaries.findByDictCode("DICT_MODEL_TEXT")).thenReturn(List.of(text));
		AiModelTypes types = new AiModelTypes(dictionaries);
		AiModelRepository models = mock(AiModelRepository.class);
		AiModel model = new AiModel();
		model.setStatus(1);
		model.setProvider("deepseek");
		model.setModelType("0");
		model.setModelName("deepseek-chat");
		model.setApiKeyCiphertext("encrypted");
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		LLMServiceStrategy strategy = new DeepseekServiceImpl(models,
				mock(AiModelChatService.class), types);

		assertThat(strategy.available()).isTrue();
		text.setDictValue("TEXT");
		assertThat(strategy.available()).isFalse();
		model.setModelType("TEXT");
		assertThat(strategy.available()).isTrue();
		text.setStatus(0);
		assertThatThrownBy(strategy::available)
				.isInstanceOf(AiModelConfigurationException.class);
	}

	@Test
	void missingOrDuplicateDictionaryEntriesDoNotSilentlyAllowModels() {
		IDictRepository dictionaries = mock(IDictRepository.class);
		AiModelTypes types = new AiModelTypes(dictionaries);
		when(dictionaries.findByDictCode("DICT_MODEL_TEXT")).thenReturn(List.of());
		assertThatThrownBy(() -> types.isText("0"))
				.isInstanceOf(AiModelConfigurationException.class);
		DictItem entry = new DictItem();
		entry.setDictValue("0");
		when(dictionaries.findByDictCode("DICT_MODEL_TEXT")).thenReturn(List.of(entry, entry));
		assertThatThrownBy(() -> types.isText("0"))
				.isInstanceOf(AiModelConfigurationException.class);
	}
}
