package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.chronos.ai.model.AiModel;

class DeepSeekChatModelFactoryTest {
	@Test
	void capsInteractiveReadTimeoutEvenWhenDatabaseConfigurationIsLonger() {
		AiModel model = new AiModel();
		model.setReadTimeoutMs(180_000);

		assertThat(DeepSeekChatModelFactory.effectiveReadTimeoutMs(model, 240_000))
				.isEqualTo(60_000);
	}

	@Test
	void preservesShorterConfiguredTimeoutAndCallDeadline() {
		AiModel model = new AiModel();
		model.setReadTimeoutMs(45_000);

		assertThat(DeepSeekChatModelFactory.effectiveReadTimeoutMs(model, 30_000))
				.isEqualTo(30_000);
	}
}
