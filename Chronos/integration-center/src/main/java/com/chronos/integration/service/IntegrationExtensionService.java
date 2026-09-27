package com.chronos.integration.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.integration.dao.ConnectorRepository;
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
import com.chronos.service.iService.IAuditLogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IntegrationExtensionService {
	private final ConnectorRepository connectors;
	private final SyncJobRepository jobs;
	private final SyncRunRepository runs;
	private final FieldMappingRepository mappings;
	private final SyncCursorRepository cursors;
	private final SyncReconciliationRepository reconciliations;
	private final DefaultHttpJsonProvider provider;
	private final IAuditLogService audit;

	@Transactional
	public FieldMapping saveMapping(String id, FieldMappingCommand command, String actor) {
		if (command == null || blank(command.connectorId()) || blank(command.objectType())
				|| blank(command.sourcePath()) || blank(command.targetField()) || command.versionNo() == null) {
			throw new IllegalArgumentException("字段映射参数不完整");
		}
		if (!connectors.existsById(command.connectorId())) throw new IllegalArgumentException("连接器不存在");
		FieldMapping mapping = id == null ? new FieldMapping()
				: mappings.findById(id).orElseThrow(() -> new IllegalArgumentException("字段映射不存在"));
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

	@Transactional(readOnly = true)
	public DryRunResult dryRun(String jobId) {
		SyncJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("同步任务不存在"));
		Connector connector = connectors.findById(job.getConnectorId()).orElseThrow();
		List<FieldMapping> mapping = mappings.findByConnectorIdAndObjectTypeAndVersionNoOrderByTargetField(
				connector.getId(), "DEFAULT", 1);
		provider.validateConfig(connector.getConfigJson());
		return new DryRunResult(job.getId(), connector.getId(), mapping.size(), job.getCursorValue(), "READY");
	}

	@Transactional
	public SyncReconciliation reconcile(String runId, String objectType, long sourceCount,
			long acceptedCount, long rejectedCount, long missingCount, String actor) {
		if (!runs.existsById(runId)) throw new IllegalArgumentException("运行记录不存在");
		if (sourceCount < 0 || acceptedCount < 0 || rejectedCount < 0 || missingCount < 0
				|| acceptedCount + rejectedCount > sourceCount) {
			throw new IllegalArgumentException("对账数量不一致");
		}
		SyncReconciliation reconciliation = new SyncReconciliation();
		reconciliation.setRunId(runId);
		reconciliation.setObjectType(objectType);
		reconciliation.setSourceCount(sourceCount);
		reconciliation.setAcceptedCount(acceptedCount);
		reconciliation.setRejectedCount(rejectedCount);
		reconciliation.setMissingCount(missingCount);
		reconciliation.setStatus("COMPLETED");
		SyncReconciliation saved = reconciliations.save(reconciliation);
		audit.log(actor, "INTEGRATION_RECONCILIATION", runId);
		return saved;
	}

	@Transactional
	public SyncCursor advanceCursor(String jobId, String cursor, String actor) {
		SyncCursor value = cursors.findByJobId(jobId).orElseGet(SyncCursor::new);
		value.setJobId(jobId);
		value.setCursorValue(cursor);
		value.setLastSuccessAt(LocalDateTime.now());
		SyncCursor saved = cursors.save(value);
		audit.log(actor, "INTEGRATION_CURSOR_ADVANCE", jobId);
		return saved;
	}

	private static boolean blank(String value) { return value == null || value.isBlank(); }
	public record FieldMappingCommand(String connectorId, String objectType, String sourcePath,
			String targetField, String transformCode, Boolean required, Integer versionNo) {}
	public record DryRunResult(String jobId, String connectorId, int mappingCount, String cursor, String status) {}
}
