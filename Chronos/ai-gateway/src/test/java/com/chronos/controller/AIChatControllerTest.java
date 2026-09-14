package com.chronos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronos.commons.model.ResultData;
import com.chronos.service.factory.LLMServiceStrategy;

class AIChatControllerTest {

	private LLMServiceStrategy configuredModel;
	private AIChatController controller;

	@BeforeEach
	void setUp() {
		configuredModel = mock(LLMServiceStrategy.class);
		controller = new AIChatController(configuredModel);
	}

	@Test
	void shouldReportAvailableDatabaseDefaultModel() {
		when(configuredModel.available()).thenReturn(true);
		when(configuredModel.provider()).thenReturn("deepseek");

		Map<String, Object> status = statusData(controller.status());

		assertThat(status)
				.containsEntry("provider", "deepseek")
				.containsEntry("available", true)
				.doesNotContainKeys("apiKey", "baseUrl", "modelId");
	}

	@Test
	void shouldNotReportAvailableWithoutDatabaseDefault() {
		when(configuredModel.available()).thenReturn(false);
		when(configuredModel.provider()).thenReturn("deepseek");

		Map<String, Object> status = statusData(controller.status());

		assertThat(status)
				.containsEntry("provider", "deepseek")
				.containsEntry("available", false);
	}

	@Test
	void shouldReportUnavailableWhenNoUsableModelExists() {
		when(configuredModel.available()).thenReturn(false);
		when(configuredModel.provider()).thenReturn("deepseek");

		Map<String, Object> status = statusData(controller.status());

		assertThat(status)
				.containsEntry("provider", "deepseek")
				.containsEntry("available", false);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> statusData(ResultData<Map<String, Object>> response) {
		assertThat(response.getCode()).isEqualTo("200");
		return response.getData();
	}
}
