package com.chronos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronos.ai.dao.AiModelRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.commons.model.ResultData;
import com.chronos.service.factory.LLMServiceStrategy;

class AIChatControllerTest {

	private LLMServiceStrategy legacyModel;
	private AiModelRepository models;
	private AIChatController controller;

	@BeforeEach
	void setUp() {
		legacyModel = mock(LLMServiceStrategy.class);
		models = mock(AiModelRepository.class);
		controller = new AIChatController(legacyModel, models);
	}

	@Test
	void shouldReportAvailableDatabaseDefaultModel() {
		AiModel model = availableModel();
		when(models.findFirstDefault()).thenReturn(Optional.of(model));
		when(legacyModel.available()).thenReturn(false);

		Map<String, Object> status = statusData(controller.status());

		assertThat(status)
				.containsEntry("provider", "deepseek")
				.containsEntry("available", true)
				.doesNotContainKeys("apiKey", "baseUrl", "modelId");
	}

	@Test
	void shouldFallBackToLegacyModelWhenDatabaseDefaultIsUnavailable() {
		AiModel disabled = availableModel();
		disabled.setStatus(0);
		when(models.findFirstDefault()).thenReturn(Optional.of(disabled));
		when(legacyModel.available()).thenReturn(true);
		when(legacyModel.provider()).thenReturn("deepseek");

		Map<String, Object> status = statusData(controller.status());

		assertThat(status)
				.containsEntry("provider", "deepseek")
				.containsEntry("available", true);
	}

	@Test
	void shouldReportUnavailableWhenNoUsableModelExists() {
		when(models.findFirstDefault()).thenReturn(Optional.empty());
		when(legacyModel.available()).thenReturn(false);
		when(legacyModel.provider()).thenReturn("deepseek");

		Map<String, Object> status = statusData(controller.status());

		assertThat(status)
				.containsEntry("provider", "deepseek")
				.containsEntry("available", false);
	}

	private AiModel availableModel() {
		AiModel model = new AiModel();
		model.setStatus(1);
		model.setProvider("deepseek");
		model.setModelName("deepseek-chat");
		model.setApiKey("secret-for-test");
		return model;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> statusData(ResultData<Map<String, Object>> response) {
		assertThat(response.getCode()).isEqualTo("200");
		return response.getData();
	}
}
