package com.chronos.integration.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.integration.dao.ConnectorRepository;
import com.chronos.integration.dao.CredentialRepository;
import com.chronos.integration.dao.FieldMappingRepository;
import com.chronos.integration.dao.SyncCursorRepository;
import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncReconciliationRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.Connector;
import com.chronos.integration.model.FieldMapping;
import com.chronos.integration.model.SyncCursor;
import com.chronos.integration.model.SyncJob;
import com.chronos.integration.model.SyncReconciliation;
import com.chronos.integration.model.SyncRun;
import com.chronos.integration.security.PlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;

/**
 * Generic mapping/cursor extension layer. It deliberately keeps fetch and
 * mapping side effects separate from cursor persistence so dry-runs cannot
 * accidentally consume a page.
 */
@Service
public class IntegrationExtensionService {
	private final ConnectorRepository connectors;
	private final SyncJobRepository jobs;
	private final SyncRunRepository runs;
	private final FieldMappingRepository mappings;
	private final SyncCursorRepository cursors;
	private final SyncReconciliationRepository reconciliations;
	private final CredentialRepository credentials;
	private final PlatformSecretCipher cipher;
	private final HttpJsonProvider provider;
	private final ObjectMapper objectMapper;
	private final IAuditLogService audit;

	public IntegrationExtensionService(ConnectorRepository connectors, SyncJobRepository jobs,
			SyncRunRepository runs, FieldMappingRepository mappings, SyncCursorRepository cursors,
			SyncReconciliationRepository reconciliations, CredentialRepository credentials,
			PlatformSecretCipher cipher, HttpJsonProvider provider, ObjectMapper objectMapper,
			IAuditLogService audit) {
		this.connectors = connectors;
		this.jobs = jobs;
		this.runs = runs;
		this.mappings = mappings;
		this.cursors = cursors;
		this.reconciliations = reconciliations;
		this.credentials = credentials;
		this.cipher = cipher;
		this.provider = provider;
		this.objectMapper = objectMapper;
		this.audit = audit;
	}

	@Transactional
	public FieldMapping saveMapping(String id, FieldMappingCommand command, String actor) {
		if (command == null || blank(command.connectorId()) || blank(command.objectType())
				|| blank(command.sourcePath()) || blank(command.targetField())
				|| command.versionNo() == null || command.versionNo() < 1) {
			throw new IntegrationBoundaryException("INVALID_MAPPING", "字段映射参数不完整");
		}
		if (!connectors.existsById(command.connectorId())) {
			throw new IntegrationBoundaryException("CONNECTOR_NOT_FOUND", "连接器不存在");
		}
		FieldMapping mapping = id == null ? new FieldMapping()
				: mappings.findById(id).orElseThrow(
						() -> new IntegrationBoundaryException("MAPPING_NOT_FOUND", "字段映射不存在"));
		mapping.setConnectorId(command.connectorId());
		mapping.setObjectType(command.objectType().trim());
		mapping.setSourcePath(command.sourcePath().trim());
		mapping.setTargetField(command.targetField().trim());
		mapping.setTransformCode(blank(command.transformCode()) ? null : command.transformCode().trim());
		mapping.setRequired(Boolean.TRUE.equals(command.required()));
		mapping.setVersionNo(command.versionNo());
		FieldMapping saved = mappings.save(mapping);
		audit.log(actor, "INTEGRATION_MAPPING_SAVE", saved.getId());
		return saved;
	}

	public DryRunResult dryRun(String jobId) {
		SyncJob job = findJob(jobId);
		Connector connector = findConnector(job.getConnectorId());
		MappingSet mappingSet = mappingSet(connector, job);
		provider.validateConfig(requiredConfig(connector));
		provider.validateEndpoint(connector.getBaseUrl(), connector.getAllowedHost());

		String cursorBefore = currentCursor(job);
		HttpJsonProvider.ProviderPage page = provider.fetchPagePage(connector.getBaseUrl(), job.getRequestPath(),
				cursorBefore, batchSize(job), idempotencyKey(job, "dry-run"), credentials(connector.getId()));
		MappedBatch mapped = mapBatch(page.records(), mappingSet.mappings());
		return new DryRunResult(job.getId(), connector.getId(), mappingSet.objectType(),
				mappingSet.versionNo(), cursorBefore, page.nextCursor(), mapped.fetched(),
				mapped.mapped().size(), mapped.requiredFailures(), mapped.diff(),
				false, mapped.requiredFailures().isEmpty() ? "READY" : "REQUIRED_FIELDS_MISSING");
	}

	/**
	 * Executes one fetched batch. The cursor is written only after every source
	 * item maps successfully and the run has been marked successful.
	 */
	public SyncRun executeBatch(String jobId, String actor) {
		SyncJob job = findJob(jobId);
		Connector connector = findConnector(job.getConnectorId());
		MappingSet mappingSet = mappingSet(connector, job);
		provider.validateConfig(requiredConfig(connector));
		provider.validateEndpoint(connector.getBaseUrl(), connector.getAllowedHost());

		SyncRun run = new SyncRun();
		run.setJobId(jobId);
		run.setStartedAt(LocalDateTime.now());
		try {
			String cursorBefore = currentCursor(job);
			HttpJsonProvider.ProviderPage page = provider.fetchPagePage(connector.getBaseUrl(), job.getRequestPath(),
					cursorBefore, batchSize(job), idempotencyKey(job, "run"), credentials(connector.getId()));
			MappedBatch mapped = mapBatch(page.records(), mappingSet.mappings());
			run.setAttemptCount(1);
			run.setSuccessCount(mapped.mapped().size());
			run.setFailureCount(mapped.requiredFailures().size());
			if (!mapped.requiredFailures().isEmpty()) {
				run.setStatus("FAILED");
				run.setErrorMessage("required mapping validation failed");
			} else {
				run.setStatus("SUCCEEDED");
			}
			run.setFinishedAt(LocalDateTime.now());
			SyncRun saved = runs.save(run);
			if (saved == null) {
				saved = run;
			}
			if ("SUCCEEDED".equals(saved.getStatus()) && !blank(page.nextCursor())) {
				advanceCursor(jobId, page.nextCursor(), actor);
			}
			return saved;
		} catch (RuntimeException failure) {
			run.setStatus("FAILED");
			run.setErrorMessage(IntegrationService.safeMessage(failure));
			run.setFinishedAt(LocalDateTime.now());
			SyncRun saved = runs.save(run);
			return saved == null ? run : saved;
		}
	}

	@Transactional
	public SyncReconciliation reconcile(String runId, String objectType, long sourceCount,
			long acceptedCount, long rejectedCount, long missingCount, String actor) {
		if (!runs.existsById(runId)) {
			throw new IntegrationBoundaryException("RUN_NOT_FOUND", "运行记录不存在");
		}
		if (blank(objectType) || sourceCount < 0 || acceptedCount < 0 || rejectedCount < 0
				|| missingCount < 0 || acceptedCount + rejectedCount + missingCount != sourceCount) {
			throw new IntegrationBoundaryException("INVALID_RECONCILIATION", "对账数量必须完整匹配来源数量");
		}
		SyncReconciliation reconciliation = new SyncReconciliation();
		reconciliation.setRunId(runId);
		reconciliation.setObjectType(objectType.trim());
		reconciliation.setSourceCount(sourceCount);
		reconciliation.setAcceptedCount(acceptedCount);
		reconciliation.setRejectedCount(rejectedCount);
		reconciliation.setMissingCount(missingCount);
		reconciliation.setStatus(missingCount == 0 ? "COMPLETED" : "MISMATCH");
		SyncReconciliation saved = reconciliations.save(reconciliation);
		audit.log(actor, "INTEGRATION_RECONCILIATION", runId);
		return saved;
	}

	public List<SyncReconciliation> reconciliations(String runId) {
		return reconciliations.findByRunIdOrderByCreateTimeDesc(runId);
	}

	@Transactional
	public SyncCursor advanceCursor(String jobId, String cursor, String actor) {
		if (blank(jobId) || blank(cursor)) {
			throw new IntegrationBoundaryException("INVALID_CURSOR", "cursor is required");
		}
		SyncCursor value = cursors.findByJobId(jobId).orElseGet(SyncCursor::new);
		value.setJobId(jobId);
		value.setCursorValue(cursor);
		value.setLastSuccessAt(LocalDateTime.now());
		SyncCursor saved = cursors.save(value);
		audit.log(actor, "INTEGRATION_CURSOR_ADVANCE", jobId);
		return saved;
	}

	private MappingSet mappingSet(Connector connector, SyncJob job) {
		String objectType = blank(job.getObjectType()) ? "DEFAULT" : job.getObjectType().trim();
		int versionNo = job.getMappingVersionNo() == null ? 1 : job.getMappingVersionNo();
		if (versionNo < 1) {
			throw new IntegrationBoundaryException("INVALID_MAPPING_VERSION", "mapping version must be positive");
		}
		return new MappingSet(objectType, versionNo,
				mappings.findByConnectorIdAndObjectTypeAndVersionNoOrderByTargetField(
						connector.getId(), objectType, versionNo));
	}

	private MappedBatch mapBatch(List<Map<String, Object>> records, List<FieldMapping> mapping) {
		List<RequiredFailure> failures = new java.util.ArrayList<>();
		List<DiffEntry> diff = new java.util.ArrayList<>();
		int index = 0;
		for (Map<String, Object> record : records) {
			JsonNode source = objectMapper.valueToTree(record);
			String sourceKey = sourceKey(source, index);
			try {
				Map<String, Object> mapped = new LinkedHashMap<>(provider.map(source, mapping));
				diff.add(new DiffEntry(sourceKey, "UPSERT", mapped));
			} catch (IntegrationBoundaryException ex) {
				failures.add(new RequiredFailure(sourceKey, ex.getCode(), ex.getMessage()));
			}
			index++;
		}
		return new MappedBatch(records.size(), diff, failures);
	}

	private Map<String, String> credentials(String connectorId) {
		if (credentials == null || cipher == null) {
			return Map.of();
		}
		Map<String, String> headers = new LinkedHashMap<>();
		for (var credential : credentials.findAllByConnectorIdOrderByKeyName(connectorId)) {
			String key = credential.getKeyName();
			if (blank(key) || key.contains("\r") || key.contains("\n")) {
				throw new IntegrationBoundaryException("INVALID_CREDENTIAL_HEADER", "credential header is invalid");
			}
			String value = cipher.decrypt(credential.getSecretCiphertext());
			if (value == null || value.contains("\r") || value.contains("\n")) {
				throw new IntegrationBoundaryException("INVALID_CREDENTIAL_VALUE", "credential value is invalid");
			}
			headers.put(key, value);
		}
		return Map.copyOf(headers);
	}

	private SyncJob findJob(String jobId) {
		return jobs.findById(jobId)
				.orElseThrow(() -> new IntegrationBoundaryException("JOB_NOT_FOUND", "同步任务不存在"));
	}

	private Connector findConnector(String connectorId) {
		return connectors.findById(connectorId)
				.orElseThrow(() -> new IntegrationBoundaryException("CONNECTOR_NOT_FOUND", "连接器不存在"));
	}

	private String currentCursor(SyncJob job) {
		return cursors.findByJobId(job.getId()).map(SyncCursor::getCursorValue)
				.filter(value -> !blank(value)).orElse(job.getCursorValue());
	}

	private static String requiredConfig(Connector connector) {
		if (!"HTTP_JSON".equalsIgnoreCase(
				blank(connector.getProviderCode()) ? "" : connector.getProviderCode().trim())) {
			throw new IntegrationBoundaryException("UNSUPPORTED_PROVIDER",
					"connector provider is not installed");
		}
		return Objects.requireNonNullElse(connector.getConfigJson(), "{}");
	}

	private static int batchSize(SyncJob job) {
		return Math.max(1, Math.min(1000, job.getBatchSize() == null ? 100 : job.getBatchSize()));
	}

	private static String idempotencyKey(SyncJob job, String suffix) {
		String template = job.getIdempotencyKeyTemplate();
		return blank(template) ? job.getId() + ":" + suffix
				: template.replace("{jobId}", job.getId()).replace("{cursor}", Objects.toString(job.getCursorValue(), ""));
	}

	private static String sourceKey(JsonNode source, int index) {
		for (String name : List.of("id", "externalId", "external_id", "key")) {
			JsonNode value = source.get(name);
			if (value != null && value.isValueNode() && !value.isNull()) {
				return value.asText();
			}
		}
		return "index:" + index;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public record FieldMappingCommand(String connectorId, String objectType, String sourcePath,
			String targetField, String transformCode, Boolean required, Integer versionNo) {
	}

	public record DryRunResult(String jobId, String connectorId, String objectType, int mappingVersionNo,
			String cursorBefore, String nextCursor, int fetchedCount, int mappedCount,
			List<RequiredFailure> requiredFailures, List<DiffEntry> diff, boolean cursorAdvanced,
			String status) {
	}

	public record RequiredFailure(String sourceKey, String code, String message) {
	}

	public record DiffEntry(String sourceKey, String operation, Map<String, Object> fields) {
		public DiffEntry {
			fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
		}
	}

	private record MappingSet(String objectType, int versionNo, List<FieldMapping> mappings) {
	}

	private record MappedBatch(int fetched, List<DiffEntry> diff, List<RequiredFailure> requiredFailures) {
		List<DiffEntry> mapped() {
			return diff;
		}
	}
}
