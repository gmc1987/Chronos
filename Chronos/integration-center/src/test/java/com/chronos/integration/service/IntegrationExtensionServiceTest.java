package com.chronos.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.integration.dao.ConnectorRepository;
import com.chronos.integration.dao.CredentialRepository;
import com.chronos.integration.dao.FieldMappingRepository;
import com.chronos.integration.dao.SyncCursorRepository;
import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncReconciliationRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.Connector;
import com.chronos.integration.model.Credential;
import com.chronos.integration.model.FieldMapping;
import com.chronos.integration.model.SyncCursor;
import com.chronos.integration.model.SyncJob;
import com.chronos.integration.model.SyncRun;
import com.chronos.integration.security.PlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;

class IntegrationExtensionServiceTest {
	private final ConnectorRepository connectors = mock(ConnectorRepository.class);
	private final SyncJobRepository jobs = mock(SyncJobRepository.class);
	private final SyncRunRepository runs = mock(SyncRunRepository.class);
	private final FieldMappingRepository mappings = mock(FieldMappingRepository.class);
	private final SyncCursorRepository cursors = mock(SyncCursorRepository.class);
	private final SyncReconciliationRepository reconciliations = mock(SyncReconciliationRepository.class);
	private final CredentialRepository credentials = mock(CredentialRepository.class);
	private final PlatformSecretCipher cipher = mock(PlatformSecretCipher.class);
	private final HttpJsonProvider provider = mock(HttpJsonProvider.class);
	private final IAuditLogService audit = mock(IAuditLogService.class);
	private IntegrationExtensionService service;
	private SyncJob job;
	private Connector connector;

	@BeforeEach
	void setUp() {
		service = new IntegrationExtensionService(connectors, jobs, runs, mappings, cursors,
				reconciliations, credentials, cipher, provider, new ObjectMapper(), audit);
		job = new SyncJob();
		job.setId("job");
		job.setConnectorId("connector");
		job.setRequestPath("/records");
		job.setObjectType("STUDENT");
		job.setMappingVersionNo(2);
		job.setBatchSize(10);
		job.setCursorValue("c0");
		connector = new Connector();
		connector.setId("connector");
		connector.setProviderCode("HTTP_JSON");
		connector.setBaseUrl("https://example.com");
		connector.setConfigJson("{}");
		when(jobs.findById("job")).thenReturn(Optional.of(job));
		when(connectors.findById("connector")).thenReturn(Optional.of(connector));
		when(cursors.findByJobId("job")).thenReturn(Optional.empty());
		when(mappings.findByConnectorIdAndObjectTypeAndVersionNoOrderByTargetField(
				"connector", "STUDENT", 2)).thenReturn(List.of(mapping("/id", "id", true)));
		when(credentials.findAllByConnectorIdOrderByKeyName("connector")).thenReturn(List.of());
		when(runs.save(any(SyncRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void dryRunFetchesMapsAndReportsDiffWithoutAdvancingCursor() {
		when(provider.fetchPagePage(eq("https://example.com"), eq("/records"), eq("c0"),
				eq(10), any(), eq(Map.of())))
				.thenReturn(new HttpJsonProvider.ProviderPage(
						List.of(Map.of("id", "student-1")), "c1"));
		when(provider.map(any(), any())).thenReturn(Map.of("id", "student-1"));

		var result = service.dryRun("job");

		assertThat(result.fetchedCount()).isEqualTo(1);
		assertThat(result.mappedCount()).isEqualTo(1);
		assertThat(result.cursorBefore()).isEqualTo("c0");
		assertThat(result.nextCursor()).isEqualTo("c1");
		assertThat(result.cursorAdvanced()).isFalse();
		assertThat(result.diff()).singleElement().extracting(
				IntegrationExtensionService.DiffEntry::operation).isEqualTo("UPSERT");
		verify(cursors, never()).save(any());
	}

	@Test
	void successfulBatchAdvancesCursorOnlyAfterRunIsSuccessful() {
		when(provider.fetchPagePage(any(), any(), eq("c0"), eq(10), any(), eq(Map.of())))
				.thenReturn(new HttpJsonProvider.ProviderPage(
						List.of(Map.of("id", "student-1")), "c1"));
		when(provider.map(any(), any())).thenReturn(Map.of("id", "student-1"));
		when(cursors.save(any(SyncCursor.class))).thenAnswer(invocation -> invocation.getArgument(0));

		SyncRun result = service.executeBatch("job", "actor");

		assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
		verify(cursors).save(any(SyncCursor.class));
	}

	@Test
	void requiredMappingFailureDoesNotAdvanceCursor() {
		when(provider.fetchPagePage(any(), any(), eq("c0"), eq(10), any(), eq(Map.of())))
				.thenReturn(new HttpJsonProvider.ProviderPage(
						List.of(Map.of("id", "student-1")), "c1"));
		when(provider.map(any(), any())).thenThrow(new IntegrationBoundaryException(
				"REQUIRED_MAPPING_MISSING", "required source field is missing"));

		SyncRun result = service.executeBatch("job", "actor");

		assertThat(result.getStatus()).isEqualTo("FAILED");
		verify(cursors, never()).save(any());
	}

	@Test
	void outboundBatchReusesDecryptedCredentialsWithoutReturningThem() {
		Credential credential = new Credential();
		credential.setConnectorId("connector");
		credential.setKeyName("Authorization");
		credential.setSecretCiphertext("v1:cipher");
		when(credentials.findAllByConnectorIdOrderByKeyName("connector")).thenReturn(List.of(credential));
		when(cipher.decrypt("v1:cipher")).thenReturn("Bearer secret");
		when(provider.fetchPagePage(any(), any(), eq("c0"), eq(10), any(),
				eq(Map.of("Authorization", "Bearer secret"))))
				.thenReturn(new HttpJsonProvider.ProviderPage(List.of(), null));
		when(provider.map(any(), any())).thenReturn(Map.of());

		var result = service.dryRun("job");

		assertThat(result.toString()).doesNotContain("Bearer secret", "v1:cipher");
		verify(cipher).decrypt("v1:cipher");
	}

	private static FieldMapping mapping(String sourcePath, String targetField, boolean required) {
		FieldMapping mapping = new FieldMapping();
		mapping.setConnectorId("connector");
		mapping.setObjectType("STUDENT");
		mapping.setVersionNo(2);
		mapping.setSourcePath(sourcePath);
		mapping.setTargetField(targetField);
		mapping.setRequired(required);
		return mapping;
	}
}
