package com.chronos.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.chronos.integration.dao.*;
import com.chronos.integration.security.FailClosedPlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class IntegrationServiceCredentialBoundaryTest {
	@Test
	void credentialConfigurationFailsClosedBeforeAnyPersistence() {
		ConnectorRepository connectors = mock(ConnectorRepository.class);
		IntegrationService service = new IntegrationService(
				connectors,
				mock(SyncJobRepository.class),
				mock(SyncRunRepository.class),
				mock(SyncItemErrorRepository.class),
				mock(DeadLetterRepository.class),
				new FailClosedPlatformSecretCipher(),
				mock(IAuditLogService.class),
				WebClient.builder());

		assertThatThrownBy(() -> service.configureCredential(
				"QA-CONNECTOR-20260920", "token", "qa-secret", "qa-admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cipher is not configured");

		verifyNoInteractions(connectors);
	}
}
