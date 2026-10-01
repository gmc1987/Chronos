package com.chronos.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.integration.dao.ConnectorRepository;
import com.chronos.integration.dao.CredentialRepository;
import com.chronos.integration.dao.DeadLetterRepository;
import com.chronos.integration.dao.SyncItemErrorRepository;
import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.Connector;
import com.chronos.integration.model.SyncJob;
import com.chronos.integration.security.PlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class IntegrationServiceBoundaryTest {
	private final ConnectorRepository connectors = mock(ConnectorRepository.class);
	private final SyncJobRepository jobs = mock(SyncJobRepository.class);
	private final IntegrationService service = new IntegrationService(connectors, jobs,
			mock(SyncRunRepository.class), mock(SyncItemErrorRepository.class),
			mock(DeadLetterRepository.class), mock(CredentialRepository.class),
			mock(PlatformSecretCipher.class), mock(IAuditLogService.class),
			mock(WebClient.Builder.class));

	@Test
	void rejectsProviderTypesWithoutInstalledProvider() {
		Connector connector = connector("SMS");

		assertThatThrownBy(() -> service.saveConnector(connector, "actor"))
				.isInstanceOf(IntegrationBoundaryException.class)
				.satisfies(error -> {
					IntegrationBoundaryException boundary = (IntegrationBoundaryException) error;
					assertThat(boundary.getCode()).isEqualTo("UNSUPPORTED_CONNECTOR_TYPE");
				});
	}

	@Test
	void rejectsInvalidConfigJsonAndDoesNotPersist() {
		Connector connector = connector("HTTP");
		connector.setConfigJson("not-json");

		assertThatThrownBy(() -> service.saveConnector(connector, "actor"))
				.isInstanceOf(IntegrationBoundaryException.class)
				.hasMessage("configJson must be valid JSON");
	}

	@Test
	void normalizesSupportedTypeAndPersistsValidatedConfig() {
		Connector connector = connector(" http ");
		when(connectors.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Connector saved = service.saveConnector(connector, "actor");

		assertThat(saved.getType()).isEqualTo("HTTP");
	}

	@Test
	void rejectsAbsoluteOrHeaderInjectingJobPaths() {
		SyncJob job = new SyncJob();
		job.setConnectorId("connector");
		job.setCronExpression("0 * * * * *");
		job.setRequestPath("https://evil.example");
		when(connectors.existsById("connector")).thenReturn(true);

		assertThatThrownBy(() -> service.saveJob(job, "actor"))
				.isInstanceOf(IntegrationBoundaryException.class)
				.hasMessage("request path must be a relative HTTP path");
	}

	@Test
	void redactsSecretsAndUrlsFromFailureMessages() {
		String safe = IntegrationService.safeMessage(
				new IllegalStateException("POST https://secret.example?token=abc Authorization: Bearer xyz"));

		assertThat(safe).doesNotContain("secret.example", "abc", "xyz")
				.contains("[URL_REDACTED]");
	}

	private Connector connector(String type) {
		Connector connector = new Connector();
		connector.setType(type);
		connector.setName("test");
		connector.setBaseUrl("https://example.com");
		connector.setConfigJson("{}");
		return connector;
	}
}
