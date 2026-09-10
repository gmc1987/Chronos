package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.workflow.IWorkflowDelegationRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceParticipantRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.Idao.workflow.IWorkflowTaskCandidateRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowTask;
import java.util.Optional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableRuntimeCoordinatorTest {
	private TaskService taskService;
	private TaskQuery taskQuery;
	private IWorkflowNodeRepository nodes;
	private FlowableRuntimeCoordinator coordinator;

	@BeforeEach
	void setUp() {
		taskService = mock(TaskService.class);
		taskQuery = mock(TaskQuery.class);
		nodes = mock(IWorkflowNodeRepository.class);
		when(taskService.createTaskQuery()).thenReturn(taskQuery);
		when(taskQuery.taskId("engine-task-1")).thenReturn(taskQuery);
		when(taskQuery.active()).thenReturn(taskQuery);
		coordinator = new FlowableRuntimeCoordinator(
				mock(RuntimeService.class),
				taskService,
				mock(HistoryService.class),
				mock(IWorkflowInstanceRepository.class),
				mock(IWorkflowTaskRepository.class),
				mock(IWorkflowInstanceParticipantRepository.class),
				mock(IWorkflowTaskCandidateRepository.class),
				mock(IWorkflowDelegationRepository.class),
				nodes,
				mock(WorkflowAssigneeResolver.class));
	}

	@Test
	void reportsAlreadyClaimedTaskAsStateConflict() {
		Task engineTask = mock(Task.class);
		when(engineTask.getAssignee()).thenReturn("first.approver");
		when(taskQuery.singleResult()).thenReturn(engineTask);

		assertThatThrownBy(() -> coordinator.claim(task(), "second.approver"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("任务已被 first.approver 认领");
	}

	@Test
	void reportsCompletedEngineTaskAsStateConflict() {
		when(taskQuery.singleResult()).thenReturn(null);

		assertThatThrownBy(() -> coordinator.approve(
				new WorkflowInstance(),
				task(),
				"approver",
				"重复提交"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Flowable 任务已处理，请刷新待办列表");
	}

	@Test
	void rejectsDynamicAddSignForSingleApprovalNode() {
		Task engineTask = mock(Task.class);
		when(engineTask.getAssignee()).thenReturn("approver");
		when(taskQuery.singleResult()).thenReturn(engineTask);

		WorkflowNode node = new WorkflowNode();
		node.setPropertiesJson("{\"approvalMode\":\"SINGLE\"}");
		when(nodes.findByFlowIdAndNodeKey("definition-1", "approval-1"))
				.thenReturn(Optional.of(node));

		WorkflowInstance instance = new WorkflowInstance();
		instance.setDefinitionId("definition-1");
		instance.setEngineInstanceId("engine-instance-1");
		WorkflowTask task = task();
		task.setNodeKey("approval-1");
		task.setAssignee("approver");

		assertThatThrownBy(() -> coordinator.addSign(
				instance,
				task,
				"approver",
				"added-user",
				"增加会签人"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("动态加签要求审批节点配置为多人会签模式");
	}

	private WorkflowTask task() {
		WorkflowTask task = new WorkflowTask();
		task.setEngineTaskId("engine-task-1");
		return task;
	}
}
