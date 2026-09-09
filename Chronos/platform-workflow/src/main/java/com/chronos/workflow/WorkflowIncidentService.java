package com.chronos.workflow;

import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowEdgeRepository;
import com.chronos.Idao.workflow.IWorkflowIncidentRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.model.workflow.WorkflowEdge;
import com.chronos.model.workflow.WorkflowIncident;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将 Flowable 死信作业投影为 Chronos 事故，并提供受审计的人工恢复操作。
 * Flowable 始终是作业状态的事实来源，wf_incident 只负责查询、授权和操作留痕。
 */
@Service
public class WorkflowIncidentService {
	private static final int SCAN_BATCH_SIZE = 1000;

	private final ManagementService managementService;
	private final RuntimeService runtimeService;
	private final IWorkflowIncidentRepository incidents;
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowDefinitionRepository definitions;
	private final IWorkflowNodeRepository nodes;
	private final IWorkflowEdgeRepository edges;
	private final FlowableRuntimeCoordinator coordinator;
	private final WorkflowService workflowService;
	private final WorkflowSecurityService security;
	private final IAuditLogService audit;
	private final ObjectMapper json = new ObjectMapper();

	public WorkflowIncidentService(
			ManagementService managementService,
			RuntimeService runtimeService,
			IWorkflowIncidentRepository incidents,
			IWorkflowInstanceRepository instances,
			IWorkflowDefinitionRepository definitions,
			IWorkflowNodeRepository nodes,
			IWorkflowEdgeRepository edges,
			FlowableRuntimeCoordinator coordinator,
			WorkflowService workflowService,
			WorkflowSecurityService security,
			IAuditLogService audit) {
		this.managementService = managementService;
		this.runtimeService = runtimeService;
		this.incidents = incidents;
		this.instances = instances;
		this.definitions = definitions;
		this.nodes = nodes;
		this.edges = edges;
		this.coordinator = coordinator;
		this.workflowService = workflowService;
		this.security = security;
		this.audit = audit;
	}

	@Transactional
	public int synchronizeDeadLetters() {
		List<Job> deadJobs = managementService.createDeadLetterJobQuery()
				.orderByJobCreateTime()
				.desc()
				.listPage(0, SCAN_BATCH_SIZE);
		for (Job job : deadJobs) {
			synchronize(job);
		}
		reconcileOpenIncidents();

		// 人工重试后的作业只有在既不位于死信表、也不位于可执行表时才视为恢复完成。
		for (WorkflowIncident incident : incidents.findByStatusOrderByCreateTimeDesc("RETRYING")) {
			boolean dead = deadJob(incident.getEngineJobId()) != null;
			boolean executable = managementService.createJobQuery()
					.jobId(incident.getEngineJobId())
					.singleResult() != null;
			if (!dead && !executable) {
				resolve(incident, "SYSTEM", "自动节点重试成功");
			}
		}
		return deadJobs.size();
	}

	/**
	 * 关闭已经没有 Flowable 作业、且流程也离开失败节点的遗留事故。
	 * 早期版本可能只记录 instanceId/nodeKey，没有记录 engineJobId；这类记录不能永久占用待处理队列。
	 */
	private void reconcileOpenIncidents() {
		for (WorkflowIncident incident : incidents.findByStatusOrderByCreateTimeDesc("OPEN")) {
			WorkflowInstance instance = instances.findById(incident.getInstanceId()).orElse(null);
			if (instance == null) {
				resolve(incident, "SYSTEM", "关联流程实例已不存在，系统自动关闭遗留事故");
				continue;
			}

			String jobId = incident.getEngineJobId();
			if (jobId != null && !jobId.isBlank()) {
				boolean dead = deadJob(jobId) != null;
				boolean executable = managementService.createJobQuery()
						.jobId(jobId)
						.singleResult() != null;
				if (!dead && !executable) {
					resolve(incident, "SYSTEM", "引擎作业已结束，系统对账关闭事故");
				}
				continue;
			}

			boolean leftFailedNode = !"RUNNING".equals(instance.getStatus())
					|| !Objects.equals(instance.getCurrentNodeKey(), incident.getNodeKey());
			if (leftFailedNode) {
				resolve(incident, "SYSTEM", "流程已离开失败节点，系统对账关闭历史遗留事故");
			}
		}
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> list(String status) {
		List<WorkflowIncident> values = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
				? incidents.findAllByOrderByCreateTimeDesc()
				: incidents.findByStatusOrderByCreateTimeDesc(status.toUpperCase());
		return values.stream().map(this::view).toList();
	}

	@Transactional
	public WorkflowIncident retry(String id, String actor) {
		WorkflowIncident incident = requireOpen(id);
		requireManagePermission(incident, actor);
		Job deadJob = requireDeadJob(incident);
		int retries = node(incident)
				.map(WorkflowNode::getRetryMax)
				.map(value -> Math.max(1, value + 1))
				.orElse(1);
		managementService.moveDeadLetterJobToExecutableJob(deadJob.getId(), retries);
		incident.setStatus("RETRYING");
		incident.setRetryCount(Objects.requireNonNullElse(incident.getRetryCount(), 0) + 1);
		incident.setNextRetryAt(LocalDateTime.now());
		incident.setResolution("管理员已重新投递 Flowable 作业");
		WorkflowIncident saved = incidents.save(incident);
		audit.log(actor, "WORKFLOW_INCIDENT_RETRY", "incidentId=" + id + ", jobId=" + deadJob.getId());
		return saved;
	}

	@Transactional
	public WorkflowIncident skip(String id, String targetNodeKey, String reason, String actor) {
		WorkflowIncident incident = requireOpen(id);
		requireManagePermission(incident, actor);
		Job deadJob = requireDeadJob(incident);
		WorkflowInstance instance = requireInstance(incident);
		String target = resolveSkipTarget(instance, incident, targetNodeKey);

		// 动态迁移会取消失败活动上的作业并从指定后继节点继续。目标仅允许选择直接后继，
		// 防止管理员绕过网关跳到流程中的任意位置。
		runtimeService.createChangeActivityStateBuilder()
				.processInstanceId(instance.getEngineInstanceId())
				.moveExecutionToActivityId(deadJob.getExecutionId(), target)
				.changeState();
		if (deadJob(deadJob.getId()) != null) {
			managementService.deleteDeadLetterJob(deadJob.getId());
		}
		coordinator.synchronize(instance);
		resolve(incident, actor, "跳过失败节点并迁移到 " + target + formatReason(reason));
		audit.log(actor, "WORKFLOW_INCIDENT_SKIP", "incidentId=" + id + ", target=" + target);
		return incident;
	}

	@Transactional
	public WorkflowIncident terminate(String id, String reason, String actor) {
		WorkflowIncident incident = requireOpenOrRetrying(id);
		requireManagePermission(incident, actor);
		WorkflowInstance instance = requireInstance(incident);
		workflowService.terminate(instance.getId(), reason, actor);
		resolve(incident, actor, "终止流程实例" + formatReason(reason));
		audit.log(actor, "WORKFLOW_INCIDENT_TERMINATE", "incidentId=" + id + ", instanceId=" + instance.getId());
		return incident;
	}

	private void synchronize(Job job) {
		// 扫描结果可能在管理员操作期间已经过期，落库前必须再次确认作业仍是死信。
		Job currentJob = deadJob(job.getId());
		if (currentJob == null) {
			return;
		}
		job = currentJob;
		WorkflowInstance instance = instances.findByEngineInstanceId(job.getProcessInstanceId()).orElse(null);
		if (instance == null) {
			return;
		}
		WorkflowIncident incident = incidents.findLockedByEngineJobId(job.getId()).orElseGet(WorkflowIncident::new);
		incident.setInstanceId(instance.getId());
		incident.setNodeKey(job.getElementId());
		incident.setEngineJobId(job.getId());
		incident.setEngineInstanceId(job.getProcessInstanceId());
		incident.setExecutionId(job.getExecutionId());
		incident.setIncidentType("AUTOMATIC_NODE_FAILURE");
		incident.setStatus("OPEN");
		incident.setNextRetryAt(toLocal(job.getDuedate()));
		incident.setErrorMessage(exceptionText(job));
		incident.setContextJson(context(job));
		incident.setResolvedAt(null);
		incident.setResolvedBy(null);
		incidents.save(incident);
	}

	private Map<String, Object> view(WorkflowIncident incident) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", incident.getId());
		result.put("instanceId", incident.getInstanceId());
		result.put("engineJobId", incident.getEngineJobId());
		result.put("nodeKey", incident.getNodeKey());
		result.put("incidentType", incident.getIncidentType());
		result.put("status", incident.getStatus());
		result.put("retryCount", incident.getRetryCount());
		result.put("nextRetryAt", incident.getNextRetryAt());
		result.put("errorMessage", incident.getErrorMessage());
		result.put("resolution", incident.getResolution());
		result.put("createTime", incident.getCreateTime());
		instances.findById(incident.getInstanceId()).ifPresent(instance -> {
			result.put("businessKey", instance.getBusinessKey());
			result.put("instanceStatus", instance.getStatus());
			definitions.findById(instance.getDefinitionId()).ifPresent(definition ->
					result.put("flowName", definition.getFlowName()));
		});
		node(incident).ifPresent(node -> result.put("nodeName", node.getNodeName()));
		return result;
	}

	private java.util.Optional<WorkflowNode> node(WorkflowIncident incident) {
		return instances.findById(incident.getInstanceId())
				.flatMap(instance -> nodes.findByFlowIdAndNodeKey(instance.getDefinitionId(), incident.getNodeKey()));
	}

	private String resolveSkipTarget(
			WorkflowInstance instance,
			WorkflowIncident incident,
			String requestedTarget) {
		List<WorkflowEdge> outgoing = edges.findByFlowIdAndFromNodeKey(
				instance.getDefinitionId(),
				incident.getNodeKey());
		if (requestedTarget != null && !requestedTarget.isBlank()) {
			boolean directSuccessor = outgoing.stream()
					.anyMatch(edge -> requestedTarget.equals(edge.getToNodeKey()));
			if (!directSuccessor) {
				throw new IllegalArgumentException("跳过目标必须是失败节点的直接后继节点");
			}
			return requestedTarget;
		}
		if (outgoing.size() != 1) {
			throw new IllegalArgumentException("失败节点存在多个后继，请明确指定 targetNodeKey");
		}
		return outgoing.getFirst().getToNodeKey();
	}

	private WorkflowIncident requireOpen(String id) {
		WorkflowIncident incident = incidents.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("流程事故不存在"));
		if (!"OPEN".equals(incident.getStatus())) {
			throw new IllegalArgumentException("只有待处理事故允许执行该操作");
		}
		return incident;
	}

	private WorkflowIncident requireOpenOrRetrying(String id) {
		WorkflowIncident incident = incidents.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("流程事故不存在"));
		if (!List.of("OPEN", "RETRYING").contains(incident.getStatus())) {
			throw new IllegalArgumentException("当前事故已经结束处理");
		}
		return incident;
	}

	private WorkflowInstance requireInstance(WorkflowIncident incident) {
		return instances.findById(incident.getInstanceId())
				.orElseThrow(() -> new IllegalArgumentException("事故关联的流程实例不存在"));
	}

	private void requireManagePermission(WorkflowIncident incident, String actor) {
		if (!security.canManageInstance(actor, incident.getInstanceId())) {
			throw new org.springframework.security.access.AccessDeniedException("无权处置该流程事故");
		}
	}

	private Job requireDeadJob(WorkflowIncident incident) {
		Job job = deadJob(incident.getEngineJobId());
		if (job == null) {
			throw new IllegalArgumentException("Flowable 死信作业已不存在，请刷新事故列表");
		}
		return job;
	}

	private Job deadJob(String jobId) {
		if (jobId == null || jobId.isBlank()) {
			return null;
		}
		return managementService.createDeadLetterJobQuery()
				.jobId(jobId)
				.singleResult();
	}

	private void resolve(WorkflowIncident incident, String actor, String resolution) {
		incident.setStatus("RESOLVED");
		incident.setResolvedBy(actor);
		incident.setResolvedAt(LocalDateTime.now());
		incident.setResolution(resolution);
		incident.setNextRetryAt(null);
		incidents.save(incident);
	}

	private String exceptionText(Job job) {
		String stacktrace = managementService.getDeadLetterJobExceptionStacktrace(job.getId());
		return stacktrace == null || stacktrace.isBlank() ? job.getExceptionMessage() : stacktrace;
	}

	private String context(Job job) {
		try {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("engineJobId", job.getId());
			values.put("engineInstanceId", job.getProcessInstanceId());
			values.put("executionId", job.getExecutionId());
			values.put("nodeKey", job.getElementId());
			values.put("jobHandlerType", job.getJobHandlerType());
			values.put("retries", job.getRetries());
			return json.writeValueAsString(values);
		} catch (Exception exception) {
			return "{}";
		}
	}

	private LocalDateTime toLocal(java.util.Date value) {
		return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
	}

	private String formatReason(String reason) {
		return reason == null || reason.isBlank() ? "" : "，原因：" + reason.trim();
	}
}
