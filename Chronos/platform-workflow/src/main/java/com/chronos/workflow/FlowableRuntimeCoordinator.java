package com.chronos.workflow;

import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceParticipantRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.Idao.workflow.IWorkflowTaskCandidateRepository;
import com.chronos.Idao.workflow.IWorkflowDelegationRepository;
import com.chronos.model.workflow.WorkflowDelegation;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowInstanceParticipant;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.model.workflow.WorkflowTaskCandidate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

/**
 * Flowable 运行时与 Chronos 门户查询模型之间的适配层。
 *
 * Flowable 是 FLOWABLE 实例的唯一状态机；wf_instance 和 wf_task 只保存业务关联
 * 以及方便门户查询的投影。所有 Flowable API 都集中在这里，避免 WorkflowService
 * 的 Legacy 分支意外推进新实例。
 */
@Service
public class FlowableRuntimeCoordinator {
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final RuntimeService runtimeService;
	private final TaskService taskService;
	private final HistoryService historyService;
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowTaskRepository tasks;
	private final IWorkflowInstanceParticipantRepository participants;
	private final IWorkflowTaskCandidateRepository candidates;
	private final IWorkflowDelegationRepository delegations;
	private final IWorkflowNodeRepository nodes;
	private final WorkflowAssigneeResolver assigneeResolver;
	private final ObjectMapper json = new ObjectMapper();

	public FlowableRuntimeCoordinator(
			RuntimeService runtimeService,
			TaskService taskService,
			HistoryService historyService,
			IWorkflowInstanceRepository instances,
			IWorkflowTaskRepository tasks,
			IWorkflowInstanceParticipantRepository participants,
			IWorkflowTaskCandidateRepository candidates,
			IWorkflowDelegationRepository delegations,
			IWorkflowNodeRepository nodes,
			WorkflowAssigneeResolver assigneeResolver) {
		this.runtimeService = runtimeService;
		this.taskService = taskService;
		this.historyService = historyService;
		this.instances = instances;
		this.tasks = tasks;
		this.participants = participants;
		this.candidates = candidates;
		this.delegations = delegations;
		this.nodes = nodes;
		this.assigneeResolver = assigneeResolver;
	}

	public WorkflowInstance start(
			WorkflowDefinition definition,
			WorkflowInstance instance,
			String actor) {
		if (definition.getFlowableProcessKey() == null || definition.getFlowableProcessKey().isBlank()) {
			throw new IllegalArgumentException("当前流程版本尚未部署到 Flowable，请重新发布后再发起");
		}

		Map<String, Object> variables = variables(instance.getVariablesJson());
		variables.put("chronosInstanceId", instance.getId());
		variables.put("chronosInitiator", actor);
		prepareAssignees(definition, instance, actor, variables);

		ProcessInstance engineInstance = runtimeService.startProcessInstanceByKey(
				definition.getFlowableProcessKey(),
				instance.getBusinessKey(),
				variables);
		instance.setEngineInstanceId(engineInstance.getId());
		instances.saveAndFlush(instance);
		configureCandidateTasks(definition, instance, actor);
		return synchronize(instance);
	}

	/** 只同步当前用户相关的 Flowable 待办，避免每次打开门户都扫描全部运行实例。 */
	public void synchronizeAssignedTo(String actor) {
		Set<String> synchronizedInstances = new HashSet<>();
		List<org.flowable.task.api.Task> visible = new ArrayList<>(taskService.createTaskQuery()
				.taskAssignee(actor)
				.active()
				.list());
		visible.addAll(taskService.createTaskQuery()
				.taskCandidateUser(actor)
				.active()
				.list());
		for (org.flowable.task.api.Task engineTask : visible) {
			if (synchronizedInstances.add(engineTask.getProcessInstanceId())) {
				instances.findByEngineInstanceId(engineTask.getProcessInstanceId())
						.ifPresent(this::synchronize);
			}
		}
	}

	public WorkflowInstance synchronize(WorkflowInstance instance) {
		if (!isFlowable(instance) || instance.getEngineInstanceId() == null) {
			return instance;
		}

		List<org.flowable.task.api.Task> engineTasks = taskService.createTaskQuery()
				.processInstanceId(instance.getEngineInstanceId())
				.active()
				.list();
		Set<String> activeTaskIds = new HashSet<>();
		for (org.flowable.task.api.Task engineTask : engineTasks) {
			activeTaskIds.add(engineTask.getId());
			synchronizeTask(instance, engineTask);
		}

		// Flowable 中已经消失的待办说明已由引擎推进或取消，不能继续在门户显示为可处理。
		for (WorkflowTask projection : tasks.findByInstanceIdOrderByCreateTimeAsc(instance.getId())) {
			if (projection.getEngineTaskId() != null
					&& "PENDING".equals(projection.getStatus())
					&& !activeTaskIds.contains(projection.getEngineTaskId())) {
				projection.setStatus("COMPLETED");
				projection.setCompletedAt(LocalDateTime.now());
				tasks.save(projection);
			}
		}

		ProcessInstance running = runtimeService.createProcessInstanceQuery()
				.processInstanceId(instance.getEngineInstanceId())
				.singleResult();
		if (running == null) {
			boolean existed = historyService.createHistoricProcessInstanceQuery()
					.processInstanceId(instance.getEngineInstanceId())
					.singleResult() != null;
			if (existed && "RUNNING".equals(instance.getStatus())) {
				instance.setStatus("COMPLETED");
				instance.setCurrentNodeKey(null);
				instance.setFinishedAt(LocalDateTime.now());
			}
		} else if (!engineTasks.isEmpty()) {
			instance.setCurrentNodeKey(engineTasks.getFirst().getTaskDefinitionKey());
		}
		return instances.save(instance);
	}

	public WorkflowInstance approve(
			WorkflowInstance instance,
			WorkflowTask projection,
			String actor,
			String comment) {
		org.flowable.task.api.Task engineTask = requireEngineTask(projection);
		requireAssignee(engineTask, actor);
		if (comment != null && !comment.isBlank()) {
			taskService.addComment(engineTask.getId(), instance.getEngineInstanceId(), comment.trim());
		}
		taskService.complete(engineTask.getId(), Map.of("approved", true));
		projection.setStatus("APPROVED");
		projection.setComment(comment);
		projection.setCompletedAt(LocalDateTime.now());
		tasks.saveAndFlush(projection);
		return synchronize(instance);
	}

	public WorkflowTask transfer(
			WorkflowTask projection,
			String actor,
			String target,
			String comment) {
		org.flowable.task.api.Task engineTask = requireEngineTask(projection);
		requireAssignee(engineTask, actor);
		taskService.setAssignee(engineTask.getId(), target);
		projection.setAssignee(target);
		projection.setComment(comment);
		return tasks.save(projection);
	}

	public WorkflowTask claim(WorkflowTask projection, String actor) {
		org.flowable.task.api.Task engineTask = requireEngineTask(projection);
		if (engineTask.getAssignee() != null) {
			throw new IllegalArgumentException("任务已被 " + engineTask.getAssignee() + " 认领");
		}
		if (!candidates.existsByTaskIdAndSubjectTypeAndSubjectId(projection.getId(), "USER", actor)) {
			throw new org.springframework.security.access.AccessDeniedException("不在当前任务候选范围内");
		}
		taskService.claim(engineTask.getId(), actor);
		projection.setAssignee(actor);
		projection.setStatus("PENDING");
		return tasks.save(projection);
	}

	public WorkflowTask unclaim(WorkflowTask projection, String actor) {
		org.flowable.task.api.Task engineTask = requireEngineTask(projection);
		requireAssignee(engineTask, actor);
		taskService.unclaim(engineTask.getId());
		projection.setAssignee(null);
		projection.setStatus("CLAIMABLE");
		return tasks.save(projection);
	}

	public WorkflowInstance rejectAndTerminate(
			WorkflowInstance instance,
			WorkflowTask projection,
			String comment) {
		org.flowable.task.api.Task engineTask = requireEngineTask(projection);
		taskService.addComment(
				engineTask.getId(),
				instance.getEngineInstanceId(),
				comment == null ? "审批拒绝" : comment);
		terminate(instance, comment == null ? "审批拒绝" : comment);
		projection.setStatus("REJECTED");
		projection.setComment(comment);
		projection.setCompletedAt(LocalDateTime.now());
		tasks.saveAndFlush(projection);
		return instance;
	}

	/**
	 * 使用 Flowable 动态状态迁移执行退回。目标必须是当前 BPMN 中真实存在的人工节点，
	 * Chronos 只负责校验它是否属于允许退回的历史范围。
	 */
	public WorkflowInstance moveTo(
			WorkflowInstance instance,
			WorkflowTask projection,
			String targetNodeKey,
			String comment) {
		org.flowable.task.api.Task engineTask = requireEngineTask(projection);
		runtimeService.createChangeActivityStateBuilder()
				.processInstanceId(instance.getEngineInstanceId())
				.moveExecutionToActivityId(engineTask.getExecutionId(), targetNodeKey)
				.changeState();
		projection.setStatus("RETURNED");
		projection.setComment(comment);
		projection.setCompletedAt(LocalDateTime.now());
		tasks.saveAndFlush(projection);
		return synchronize(instance);
	}

	public void terminate(WorkflowInstance instance, String reason) {
		if (runtimeService.createProcessInstanceQuery()
				.processInstanceId(instance.getEngineInstanceId())
				.singleResult() != null) {
			runtimeService.deleteProcessInstance(instance.getEngineInstanceId(), reason);
		}
	}

	public boolean isFlowable(WorkflowInstance instance) {
		return "FLOWABLE".equals(instance.getEngineType());
	}

	private void prepareAssignees(
			WorkflowDefinition definition,
			WorkflowInstance instance,
			String actor,
			Map<String, Object> variables) {
		for (WorkflowNode node : nodes.findByFlowIdOrderByCreateTimeAsc(definition.getId())) {
			if (!Set.of("APPROVAL", "TASK", "CC").contains(node.getNodeType())) {
				continue;
			}
			List<String> assignees = new ArrayList<>(assigneeResolver.resolve(node, instance, actor));
			if (assignees.isEmpty()) {
				throw new IllegalArgumentException("节点未解析到有效处理人：" + node.getNodeName());
			}
			assignees = assignees.stream()
					.map(user -> effectiveAssignee(user, definition.getId()))
					.distinct()
					.toList();
			String suffix = safe(node.getNodeKey());
			variables.put("chronosAssignee_" + suffix, assignees.getFirst());
			variables.put("chronosAssignees_" + suffix, assignees);
		}
	}

	private void synchronizeTask(WorkflowInstance instance, org.flowable.task.api.Task engineTask) {
		WorkflowTask projection = tasks.findByEngineTaskId(engineTask.getId()).orElseGet(WorkflowTask::new);
		projection.setInstanceId(instance.getId());
		projection.setEngineTaskId(engineTask.getId());
		projection.setNodeKey(engineTask.getTaskDefinitionKey());
		projection.setNodeName(engineTask.getName() == null ? engineTask.getTaskDefinitionKey() : engineTask.getName());
		projection.setAssignee(engineTask.getAssignee());
		projection.setStatus(engineTask.getAssignee() == null ? "CLAIMABLE" : "PENDING");
		if (engineTask.getDueDate() != null) {
			projection.setDueAt(LocalDateTime.ofInstant(
					engineTask.getDueDate().toInstant(),
					ZoneId.systemDefault()));
		}
		projection = tasks.save(projection);
		for (org.flowable.identitylink.api.IdentityLink link : taskService.getIdentityLinksForTask(engineTask.getId())) {
			if ("candidate".equals(link.getType()) && link.getUserId() != null) {
				addCandidate(projection.getId(), link.getUserId());
			}
		}
		addAssigneeParticipant(instance, projection);
	}

	private void configureCandidateTasks(
			WorkflowDefinition definition,
			WorkflowInstance instance,
			String actor) {
		for (org.flowable.task.api.Task engineTask : taskService.createTaskQuery()
				.processInstanceId(instance.getEngineInstanceId())
				.taskUnassigned()
				.active()
				.list()) {
			WorkflowNode node = nodes.findByFlowIdAndNodeKey(
					definition.getId(),
					engineTask.getTaskDefinitionKey()).orElse(null);
			if (node == null || !requiresCandidateClaim(node)) {
				continue;
			}
			for (String user : assigneeResolver.resolve(node, instance, actor)) {
				taskService.addCandidateUser(engineTask.getId(), effectiveAssignee(user, definition.getId()));
			}
		}
	}

	private boolean requiresCandidateClaim(WorkflowNode node) {
		try {
			JsonNode properties = json.readTree(
					node.getPropertiesJson() == null ? "{}" : node.getPropertiesJson());
			if (properties.path("claimRequired").asBoolean(false)) {
				return true;
			}

			// 单人审批绑定角色时，所有角色成员进入候选池，由其中一人认领。
			// 禁止再按用户名排序静默选中第一人，避免任务长期固定落到同一账号。
			return "SINGLE".equals(properties.path("approvalMode").asText("SINGLE"))
					&& "ROLE".equals(properties.path("assigneeMode").asText());
		} catch (Exception exception) {
			return false;
		}
	}

	private String effectiveAssignee(String user, String definitionId) {
		LocalDateTime now = LocalDateTime.now();
		return delegations.findByDelegatorAndEnabledTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
				user,
				now,
				now).stream()
				.filter(value -> value.getDefinitionId() == null
						|| value.getDefinitionId().isBlank()
						|| definitionId.equals(value.getDefinitionId()))
				.map(WorkflowDelegation::getDelegatee)
				.findFirst()
				.orElse(user);
	}

	private void addCandidate(String taskId, String username) {
		if (candidates.existsByTaskIdAndSubjectTypeAndSubjectId(taskId, "USER", username)) {
			return;
		}
		WorkflowTaskCandidate candidate = new WorkflowTaskCandidate();
		candidate.setTaskId(taskId);
		candidate.setSubjectType("USER");
		candidate.setSubjectId(username);
		candidates.save(candidate);
	}

	private void addAssigneeParticipant(WorkflowInstance instance, WorkflowTask projection) {
		if (projection.getAssignee() == null || projection.getAssignee().isBlank()) {
			return;
		}
		if (participants.existsByInstanceIdAndUsernameAndParticipantTypeAndActiveTrue(
				instance.getId(),
				projection.getAssignee(),
				"ASSIGNEE")) {
			return;
		}
		WorkflowInstanceParticipant participant = new WorkflowInstanceParticipant();
		participant.setInstanceId(instance.getId());
		participant.setUsername(projection.getAssignee());
		participant.setParticipantType("ASSIGNEE");
		participant.setSourceTaskId(projection.getId());
		participants.save(participant);
	}

	private org.flowable.task.api.Task requireEngineTask(WorkflowTask projection) {
		if (projection.getEngineTaskId() == null) {
			throw new IllegalArgumentException("任务没有关联 Flowable 运行时任务");
		}
		org.flowable.task.api.Task task = taskService.createTaskQuery()
				.taskId(projection.getEngineTaskId())
				.active()
				.singleResult();
		if (task == null) {
			throw new IllegalArgumentException("Flowable 任务已处理或不存在");
		}
		return task;
	}

	private void requireAssignee(org.flowable.task.api.Task task, String actor) {
		if (!actor.equals(task.getAssignee())) {
			throw new org.springframework.security.access.AccessDeniedException("不是当前任务处理人");
		}
	}

	private Map<String, Object> variables(String value) {
		if (value == null || value.isBlank()) {
			return new HashMap<>();
		}
		try {
			return new HashMap<>(json.readValue(value, MAP_TYPE));
		} catch (Exception exception) {
			throw new IllegalArgumentException("流程变量不是合法 JSON", exception);
		}
	}

	private String safe(String value) {
		return value.replaceAll("[^A-Za-z0-9_]", "_");
	}
}
