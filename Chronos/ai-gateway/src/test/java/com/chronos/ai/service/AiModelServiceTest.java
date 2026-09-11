package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;

class AiModelServiceTest {
	@Test
	void updateInvalidatesRuntimeAfterChangingConfiguration() {
		AiModelRepository repository = mock(AiModelRepository.class);
		AiModelChatService runtime = mock(AiModelChatService.class);
		AiModel existing = model("model-1");
		AiModel command = model("model-1");
		command.setModelName("deepseek-reasoner");
		when(repository.findById("model-1")).thenReturn(Optional.of(existing));
		when(repository.save(any(AiModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

		new AiModelService(repository, runtime).update(command);

		verify(runtime).invalidate("model-1");
	}

	@Test
	void listRejectsUnboundedPageSize() {
		AiModelService service = new AiModelService(mock(AiModelRepository.class));

		assertThatThrownBy(() -> service.list("", "", null, 0, 101))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("每页数量");
	}

	private AiModel model(String id) {
		AiModel value = new AiModel();
		value.setId(id);
		value.setModelName("deepseek-chat");
		value.setModelType("chat");
		value.setProvider("deepseek");
		value.setApiKey("secret");
		value.setStatus(1);
		value.setIsDefault(false);
		return value;
	}
}
