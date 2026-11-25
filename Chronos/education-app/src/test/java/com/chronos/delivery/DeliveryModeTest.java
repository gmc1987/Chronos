package com.chronos.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DeliveryModeTest {
	@Test
	void opensSalesForSourceLaunchesWithoutChangingDeploymentDefaults() {
		var environment = new MockEnvironment();
		assertThat(DeliveryMode.resolve(environment, true)).isEqualTo("issuer");
		assertThat(DeliveryMode.resolve(environment, false)).isEqualTo("standard");

		environment.setActiveProfiles("docker");
		assertThat(DeliveryMode.resolve(environment, true)).isEqualTo("standard");
	}

	@Test
	void preservesExplicitConfigurationInBothLaunchModes() {
		var environment = new MockEnvironment().withProperty("chronos.delivery.mode", "customer");
		assertThat(DeliveryMode.resolve(environment, true)).isEqualTo("customer");
		assertThat(DeliveryMode.resolve(environment, false)).isEqualTo("customer");
		environment.setProperty("chronos.delivery.mode", "issuer");
		assertThat(DeliveryMode.resolve(environment, false)).isEqualTo("issuer");
	}
}
