package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chronos.education.operationagent.model.OperationAgentCheckpointMode;

class OperationAgentGovernancePolicyServiceTest {
	private final OperationAgentToolCatalog catalog = new OperationAgentToolCatalog();
	private final OperationAgentGovernancePolicyService service =
			new OperationAgentGovernancePolicyService();

	@Test
	void keepsInitializationAsInitiatorConfirmationAndDryRunAsNoCheckpoint() {
		var tool = catalog.require("iam.employee.import.managed-file");
		assertThat(service.decide(tool, Map.of()).mode())
				.isEqualTo(OperationAgentCheckpointMode.INITIATOR_CONFIRM);
		assertThat(service.decide(tool, Map.of("dryRun", true)).mode())
				.isEqualTo(OperationAgentCheckpointMode.NONE);
	}

	@Test
	void preservesIndependentReviewAndBusinessWorkflowBoundaries() {
		assertThat(service.decide(catalog.require("scheduling.review.submitted"), Map.of()).mode())
				.isEqualTo(OperationAgentCheckpointMode.DUAL_CONTROL);
		assertThat(service.decide(catalog.require("workflow.task.complete"), Map.of()).mode())
				.isEqualTo(OperationAgentCheckpointMode.BUSINESS_WORKFLOW);
	}
}
