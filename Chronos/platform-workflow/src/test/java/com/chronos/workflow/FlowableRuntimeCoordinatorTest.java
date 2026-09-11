package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableRuntimeCoordinatorTest {
	private TaskService taskService;
	private TaskQuery taskQuery;
	private RuntimeService runtimeService;
	private IWorkflowInstanceRepository instances;
	private IWorkflowTaskRepository tasks;
	private IWorkflowNodeRepository nodes;
	private FlowableRuntimeCoordinator coordinator;

	@BeforeEach
	void setUp() {
		taskService = mock(TaskService.class);
		taskQuery = mock(TaskQuery.class);
		runtimeService = mock(RuntimeService.class);
		instances = mock(IWorkflowInstanceRepository.class);
		tasks = mock(IWorkflowTaskRepository.class);
		nodes = mock(IWorkflowNodeRepository.class);
		when(taskService.createTaskQuery()).thenReturn(taskQuery);
		when(taskQuery.taskId("engine-task-1")).thenReturn(taskQuery);
		when(taskQuery.active()).thenReturn(taskQuery);
		coordinator = new FlowableRuntimeCoordinator(
				runtimeService,
				taskService,
				mock(HistoryService.class),
				instances,
				tasks,
				mock(IWorkflowInstanceParticipantRepository.class),
				mock(IWorkflowTaskCandidateRepository.class),
				mock(IWorkflowDelegationRepository.class),
				nodes,
				mock(WorkflowAssigneeResolver.class));
	}

	@Test
	void synchronizeRestoresCandidateUsersFromProcessSnapshot() {
		Task engineTask = mock(Task.class);
		when(engineTask.getId()).thenReturn("engine-task-returned");
		when(engineTask.getTaskDefinitionKey()).thenReturn("approval-1");
		when(engineTask.getName()).thenReturn("审批节点");
		when(engineTask.getAssignee()).thenReturn(null);
		when(taskQuery.processInstanceId("engine-instance-1")).thenReturn(taskQuery);
		when(taskQuery.list()).thenReturn(java.util.List.of(engineTask));
		when(taskService.getIdentityLinksForTask("engine-task-returned"))
				.thenReturn(java.util.List.of());
		when(runtimeService.getVariable(
				"engine-instance-1",
				"chronosAssignees_approval_1"))
				.thenReturn(java.util.List.of("approver-a", "approver-b"));
		when(tasks.findByEngineTaskId("engine-task-returned"))
				.thenReturn(Optional.empty());
		when(tasks.save(org.mockito.ArgumentMatchers.any(WorkflowTask.class)))
				.thenAnswer(invocation -> {
					WorkflowTask task = invocation.getArgument(0);
					task.setId("projection-1");
					return task;
				});
		when(tasks.findByInstanceIdOrderByCreateTimeAsc("instance-1"))
				.thenReturn(java.util.List.of());
		ProcessInstanceQuery processQuery = mock(ProcessInstanceQuery.class);
		ProcessInstance running = mock(ProcessInstance.class);
		when(runtimeService.createProcessInstanceQuery()).thenReturn(processQuery);
		when(processQuery.processInstanceId("engine-instance-1")).thenReturn(processQuery);
		when(processQuery.singleResult()).thenReturn(running);
		when(instances.save(org.mockito.ArgumentMatchers.any(WorkflowInstance.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		WorkflowInstance instance = new WorkflowInstance();
		instance.setId("instance-1");
		instance.setEngineType("FLOWABLE");
		instance.setEngineInstanceId("engine-instance-1");
		instance.setStatus("RUNNING");

		coordinator.synchronize(instance);

		verify(taskService).addCandidateUser("engine-task-returned", "approver-a");
		verify(taskService).addCandidateUser("engine-task-returned", "approver-b");
		verify(tasks).save(argThat(task -> "NORMAL".equals(task.getTaskKind())
				&& "CLAIMABLE".equals(task.getStatus())
				&& "approval-1".equals(task.getNodeKey())));
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
