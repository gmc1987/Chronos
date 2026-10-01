package com.chronos.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IntegrationNetworkPolicyTest {
	private final IntegrationNetworkPolicy policy = new IntegrationNetworkPolicy("");

	@Test
	void blocksLocalhostLoopbackAndMetadataDestinations() {
		assertThatThrownBy(() -> policy.validateBaseUrl("http://localhost", null))
				.isInstanceOf(IntegrationBoundaryException.class);
		assertThatThrownBy(() -> policy.validateBaseUrl("http://127.0.0.1", null))
				.isInstanceOf(IntegrationBoundaryException.class);
		assertThatThrownBy(() -> policy.validateBaseUrl("http://169.254.169.254", null))
				.isInstanceOf(IntegrationBoundaryException.class);
	}

	@Test
	void requiresConnectorHostToMatchItsAllowlist() {
		assertThatThrownBy(() -> policy.validateBaseUrl("https://example.com", "other.example.com"))
				.isInstanceOf(IntegrationBoundaryException.class)
				.hasMessage("connector host is not in the network allowlist");
	}

	@Test
	void acceptsAnExplicitExternalHost() {
		policy.validateBaseUrl("https://example.com", "example.com");
	}
}
