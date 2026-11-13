package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.chronos.education.operationagent.model.OperationAgentCheckpointMode;

class OperationAgentToolCatalogTest {
	private final OperationAgentToolCatalog catalog = new OperationAgentToolCatalog();

	@Test
	void exposesTheCompleteOpeningAndClosingLifecycleWithGovernanceMetadata() {
		assertThat(catalog.all()).extracting(OperationAgentToolCatalog.ToolDefinition::code)
				.doesNotHaveDuplicates()
				.contains(OperationAgentTeacherOnboardingTool.CODE)
				.doesNotContain(OperationAgentToolCatalog.RETIRED_TEACHER_TOOL);
		assertThat(catalog.require(OperationAgentTeacherOnboardingTool.CODE).checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.INITIATOR_CONFIRM);
		assertThat(catalog.executionOrder("iam.employee.import.managed-file"))
				.isLessThan(catalog.executionOrder(OperationAgentTeacherOnboardingTool.CODE));
		assertThatThrownBy(() -> catalog.require(OperationAgentToolCatalog.RETIRED_TEACHER_TOOL))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("旧版教师直接建档");
		assertThat(catalog.require("academic.term.create").checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.INITIATOR_CONFIRM);
		assertThat(catalog.require("academic.term.create").writeTool()).isTrue();
		assertThat(catalog.require("iam.employee.import.managed-file").approvalRequired()).isTrue();
		assertThat(catalog.require("iam.employee.import.managed-file").checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.INITIATOR_CONFIRM);
		assertThat(catalog.require("iam.department.import.managed-file").checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.INITIATOR_CONFIRM);
		assertThat(catalog.require("scheduling.review.submitted").checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.DUAL_CONTROL);
		assertThat(catalog.require("workflow.task.complete").checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.BUSINESS_WORKFLOW);
		assertThat(catalog.require("semester.context.check").checkpointMode())
				.isEqualTo(OperationAgentCheckpointMode.NONE);
		assertThat(catalog.require("iam.department.import.managed-file").writeTool()).isTrue();
		assertThat(catalog.require("scheduling.review.submitted").approvalActorExecutes()).isTrue();
		assertThat(catalog.require("admission.enroll.batch").writeTool()).isTrue();
		assertThat(catalog.require("semester.context.check").maxAttempts()).isEqualTo(2);
		assertThat(catalog.require("teaching.assignment.generate").businessStageOrder()).isEqualTo(5);
		assertThat(catalog.require("teaching.offering.generate").businessStageOrder()).isEqualTo(6);
		assertThat(catalog.require("scheduling.publish").businessStageOrder()).isEqualTo(7);
		assertThat(catalog.require("term.end.exam.check").businessStageOrder()).isEqualTo(8);
		assertThat(catalog.require("term.graduation.prepare").businessStageOrder()).isEqualTo(9);
		assertThat(catalog.require("term.end.archive.check").businessStageOrder()).isEqualTo(10);
		assertThat(catalog.require("admission.accommodation.check").businessStageOrder()).isEqualTo(4);
		assertThat(catalog.require("admission.accommodation.assign.batch").approvalRequired()).isTrue();
		assertThat(catalog.require("iam.authorization.assign").writeTool()).isTrue();
		assertThat(catalog.require("workflow.task.complete").approvalActorExecutes()).isTrue();
		assertThat(catalog.require("exam.plan.publish").riskLevel()).isEqualTo("CRITICAL");
		assertThat(catalog.require("grade.review").approvalActorExecutes()).isTrue();
		assertThat(catalog.require("grade.change.review").businessStageCode()).isEqualTo("GRADE_OPERATION");
		assertThat(catalog.executionOrder("admission.import.managed-file"))
				.isLessThan(catalog.executionOrder("admission.data.check"));
		assertThat(catalog.executionOrder("admission.data.check"))
				.isLessThan(catalog.executionOrder("admission.class.assign.batch"));
		assertThatThrownBy(() -> catalog.require("database.raw.sql"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
