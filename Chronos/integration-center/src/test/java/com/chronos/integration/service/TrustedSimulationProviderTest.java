package com.chronos.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TrustedSimulationProviderTest {
	private final TrustedSimulationProvider provider = new TrustedSimulationProvider(new ObjectMapper());

	@Test
	void returnsConfiguredPageAndCursorWithoutNetworkAccess() {
		var page = provider.fetchPage("""
				{"pages":{"START":{"records":[{"id":"one"}],"nextCursor":"c1"}}}
				""", null, 10);

		assertThat(page.records()).containsExactly(java.util.Map.of("id", "one"));
		assertThat(page.nextCursor()).isEqualTo("c1");
	}

	@Test
	void exposesConfiguredFailureAtCursor() {
		assertThatThrownBy(() -> provider.fetchPage("""
				{"failOnCursor":"c1","failureMessage":"upstream unavailable","pages":{}}
				""", "c1", 10))
				.isInstanceOf(IntegrationBoundaryException.class)
				.hasMessage("upstream unavailable");
	}
}
