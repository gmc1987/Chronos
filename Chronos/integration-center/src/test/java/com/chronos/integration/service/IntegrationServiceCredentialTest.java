package com.chronos.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.integration.dao.CredentialRepository;
import com.chronos.integration.dao.ConnectorRepository;
import com.chronos.integration.dao.DeadLetterRepository;
import com.chronos.integration.dao.SyncItemErrorRepository;
import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.Credential;
import com.chronos.integration.security.PlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class IntegrationServiceCredentialTest {
	@Test
	void encryptsBeforePersistingAndReturnsOnlyMetadataThroughControllerContract() {
		ConnectorRepository connectors = mock(ConnectorRepository.class);
		CredentialRepository credentials = mock(CredentialRepository.class);
		PlatformSecretCipher cipher = mock(PlatformSecretCipher.class);
		when(connectors.existsById("connector")).thenReturn(true);
		when(cipher.encrypt("plain-secret")).thenReturn("v1:ciphertext");
		when(credentials.findByConnectorIdAndKeyName("connector", "Authorization"))
				.thenReturn(java.util.Optional.empty());
		when(credentials.save(any(Credential.class))).thenAnswer(invocation -> invocation.getArgument(0));

		IntegrationService service = new IntegrationService(connectors, mock(SyncJobRepository.class),
				mock(SyncRunRepository.class), mock(SyncItemErrorRepository.class), mock(DeadLetterRepository.class),
				credentials, cipher, mock(IAuditLogService.class), mock(WebClient.Builder.class));

		Credential saved = service.configureCredential("connector", "Authorization", "plain-secret", "actor");

		assertThat(saved.getSecretCiphertext()).isEqualTo("v1:ciphertext");
		assertThat(saved.getSecretCiphertext()).doesNotContain("plain-secret");
		verify(cipher).encrypt("plain-secret");
		verify(credentials).save(saved);
	}
}
