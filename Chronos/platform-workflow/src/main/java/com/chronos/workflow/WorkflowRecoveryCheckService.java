package com.chronos.workflow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;

/**
 * Read-only consistency preflight for a backup/restore exercise. It intentionally
 * never starts or deletes Flowable instances; recovery is an operator decision.
 */
@Service
public class WorkflowRecoveryCheckService {
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowDefinitionRepository definitions;
	private final RuntimeService runtime;

	public WorkflowRecoveryCheckService(
			IWorkflowInstanceRepository instances,
			IWorkflowDefinitionRepository definitions,
			RuntimeService runtime) {
		this.instances = instances;
		this.definitions = definitions;
		this.runtime = runtime;
	}

	@Transactional(readOnly = true)
	public RecoveryCheck inspect() {
		long totalRunning = instances.countByStatus("RUNNING");
		var sample = instances.findTop200ByStatusOrderByCreateTimeDesc("RUNNING");
		List<RecoveryIssue> issues = new ArrayList<>();
		for (var instance : sample) {
			if (!definitions.existsById(instance.getDefinitionId())) {
				issues.add(new RecoveryIssue(instance.getId(), "DEFINITION_MISSING"));
			}
			if (!"FLOWABLE".equals(instance.getEngineType())) {
				continue;
			}
			String engineId = instance.getEngineInstanceId();
			if (engineId == null || engineId.isBlank()) {
				issues.add(new RecoveryIssue(instance.getId(), "ENGINE_ID_MISSING"));
				continue;
			}
			if (runtime.createProcessInstanceQuery()
					.processInstanceId(engineId)
					.count() == 0) {
				issues.add(new RecoveryIssue(instance.getId(), "ENGINE_RUNTIME_MISSING"));
			}
		}
		return new RecoveryCheck(
				LocalDateTime.now(),
				totalRunning,
				sample.size(),
				totalRunning > sample.size(),
				issues);
	}

	public record RecoveryIssue(String instanceId, String code) {
	}

	public record RecoveryCheck(
			LocalDateTime checkedAt,
			long runningInstances,
			int checkedInstances,
			boolean truncated,
			List<RecoveryIssue> issues) {
	}
}
