package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chronos.model.workflow.WorkflowInstance;

class WorkflowBatchApprovalServiceTest {
	@Test
	void preservesSuccessfulItemsWhenAnotherTaskFails() {
		WorkflowService workflows = mock(WorkflowService.class);
		WorkflowInstance completed = new WorkflowInstance();
		completed.setId("instance-1");
		when(workflows.completeTask("task-1", true, "同意", "teacher"))
				.thenReturn(completed);
		when(workflows.completeTask("task-2", true, "同意", "teacher"))
				.thenThrow(new IllegalArgumentException("请先提交节点表单"));

		var result = new WorkflowBatchApprovalService(workflows).approve(
				new WorkflowBatchApprovalService.BatchCommand(
						List.of("task-1", "task-2"),
						"同意"),
				"teacher");

		assertThat(result.succeeded()).isEqualTo(1);
		assertThat(result.items()).extracting(WorkflowBatchApprovalService.BatchItemResult::status)
				.containsExactly("SUCCEEDED", "FAILED");
		assertThat(result.items().get(1).message()).contains("表单");
	}

	@Test
	void rejectsDuplicatesAndOversizedBatchesBeforeAnyTaskIsRun() {
		WorkflowBatchApprovalService batch = new WorkflowBatchApprovalService(mock(WorkflowService.class));
		assertThatThrownBy(() -> batch.approve(
				new WorkflowBatchApprovalService.BatchCommand(List.of("same", "same"), ""),
				"teacher"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> batch.approve(
				new WorkflowBatchApprovalService.BatchCommand(Collections.nCopies(21, "task"), ""),
				"teacher"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
