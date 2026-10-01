package com.chronos.workflow;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;

@RestController
public class WorkflowOperationsController {
	private final WorkflowOperationsService operations;
	private final WorkflowRecoveryCheckService recoveryChecks;

	public WorkflowOperationsController(
			WorkflowOperationsService operations,
			WorkflowRecoveryCheckService recoveryChecks) {
		this.operations = operations;
		this.recoveryChecks = recoveryChecks;
	}

	@GetMapping("/admin/workflow-operations/health")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowOperationsService.OperationsHealth> health() {
		return ResultData.<WorkflowOperationsService.OperationsHealth>builder()
				.code("200")
				.msg("ok")
				.data(operations.health())
				.build();
	}

	@GetMapping("/admin/workflow-operations/recovery-check")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowRecoveryCheckService.RecoveryCheck> recoveryCheck() {
		return ResultData.<WorkflowRecoveryCheckService.RecoveryCheck>builder()
				.code("200")
				.msg("ok")
				.data(recoveryChecks.inspect())
				.build();
	}
}
