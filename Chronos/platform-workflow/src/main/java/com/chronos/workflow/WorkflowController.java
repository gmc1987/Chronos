package com.chronos.workflow;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.model.form.FormInstance;
import com.chronos.model.workflow.WorkflowAiSetting;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowDefinitionAcl;
import com.chronos.model.workflow.WorkflowDelegation;
import com.chronos.model.workflow.WorkflowEdge;
import com.chronos.model.workflow.WorkflowExecutionLog;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowIncident;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowNotification;
import com.chronos.model.workflow.WorkflowOutbox;
import com.chronos.model.workflow.WorkflowReview;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.workflow.executor.ExecutorDescriptor;
import com.chronos.workflow.executor.WorkflowExecutorRegistry;
import com.chronos.workflow.executor.WorkflowExecutionAuditService;

@RestController
public class WorkflowController {
	private final WorkflowService service;
	private final WorkflowExecutorRegistry executors;
	private final WorkflowIdempotencyService idempotency;
	private final WorkflowNotificationService notifications;
	private final WorkflowIncidentService incidents;
	private final WorkflowExecutionAuditService executionAudit;

	public WorkflowController(
			WorkflowService service,
			WorkflowExecutorRegistry executors,
			WorkflowIdempotencyService idempotency,
			WorkflowNotificationService notifications,
			WorkflowIncidentService incidents,
			WorkflowExecutionAuditService executionAudit) {
		this.service = service;
		this.executors = executors;
		this.idempotency = idempotency;
		this.notifications = notifications;
		this.incidents = incidents;
		this.executionAudit = executionAudit;
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	@GetMapping("/admin/workflows/list")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:view','workflow:manage')")
	public ResultData<Page<WorkflowDefinition>> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Principal p) {
		return ok(service.list(PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createTime")),
				p.getName()));
	}

	@GetMapping("/admin/workflows/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:view','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'VIEW')")
	public ResultData<WorkflowDefinition> get(@PathVariable String id) {
		return ok(service.get(id));
	}

	@PostMapping("/admin/workflows")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:create','workflow:manage')")
	public ResultData<WorkflowDefinition> create(@RequestBody WorkflowDefinition v, Principal p) {
		v.setId(null);
		return ok(service.save(v, p.getName()));
	}

	@PutMapping("/admin/workflows")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#v.id,'DESIGN')")
	public ResultData<WorkflowDefinition> update(@RequestBody WorkflowDefinition v, Principal p) {
		return ok(service.update(v, p.getName()));
	}

	@DeleteMapping("/admin/workflows/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:delete','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'DELETE')")
	public ResultData<Void> delete(@PathVariable String id, Principal p) {
		service.delete(id, p.getName());
		return ok(null);
	}

	@PostMapping("/admin/workflows/{id}/disable")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:publish','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'PUBLISH')")
	public ResultData<WorkflowDefinition> disable(@PathVariable String id, Principal p) {
		return ok(service.disable(id, p.getName()));
	}

	@PostMapping("/admin/workflows/{id}/versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:create','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'VIEW')")
	public ResultData<WorkflowDefinition> createVersion(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal p) {
		return ok(service.createVersion(id, body.get("version"), p.getName()));
	}

	@GetMapping("/admin/workflows/{id}/acls")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:view','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'VIEW')")
	public ResultData<List<WorkflowDefinitionAcl>> acls(@PathVariable String id) {
		return ok(service.acls(id));
	}

	@PostMapping("/admin/workflows/{id}/acls")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'MANAGE')")
	public ResultData<WorkflowDefinitionAcl> saveAcl(@PathVariable String id, @RequestBody WorkflowDefinitionAcl value,
			Principal p) {
		value.setId(null);
		value.setDefinitionId(id);
		return ok(service.saveAcl(value, p.getName()));
	}

	@DeleteMapping("/admin/workflow-acls/{aclId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canAcl(authentication.name,#aclId,'MANAGE')")
	public ResultData<Void> deleteAcl(@PathVariable String aclId, Principal p) {
		service.deleteAcl(aclId, p.getName());
		return ok(null);
	}

	@GetMapping("/admin/workflow-nodes/list")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:view','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#flowId,'VIEW')")
	public ResultData<List<WorkflowNode>> nodes(@RequestParam String flowId) {
		return ok(service.nodes(flowId));
	}

	@PostMapping("/admin/workflow-nodes")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#v.flowId,'DESIGN')")
	public ResultData<WorkflowNode> createNode(@RequestBody WorkflowNode v) {
		v.setId(null);
		return ok(service.saveNode(v));
	}

	@PutMapping("/admin/workflow-nodes")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canNode(authentication.name,#v.id,'DESIGN')")
	public ResultData<WorkflowNode> updateNode(@RequestBody WorkflowNode v) {
		return ok(service.updateNode(v));
	}

	@DeleteMapping("/admin/workflow-nodes/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canNode(authentication.name,#id,'DESIGN')")
	public ResultData<Void> deleteNode(@PathVariable String id) {
		service.deleteNode(id);
		return ok(null);
	}

	@GetMapping("/admin/workflow-edges/list")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:view','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#flowId,'VIEW')")
	public ResultData<List<WorkflowEdge>> edges(@RequestParam String flowId) {
		return ok(service.edges(flowId));
	}

	@PostMapping("/admin/workflow-edges")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#v.flowId,'DESIGN')")
	public ResultData<WorkflowEdge> createEdge(@RequestBody WorkflowEdge v) {
		v.setId(null);
		return ok(service.saveEdge(v));
	}

	@PutMapping("/admin/workflow-edges")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canEdge(authentication.name,#v.id,'DESIGN')")
	public ResultData<WorkflowEdge> updateEdge(@RequestBody WorkflowEdge v) {
		return ok(service.updateEdge(v));
	}

	@DeleteMapping("/admin/workflow-edges/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canEdge(authentication.name,#id,'DESIGN')")
	public ResultData<Void> deleteEdge(@PathVariable String id) {
		service.deleteEdge(id);
		return ok(null);
	}

	@PostMapping("/admin/workflows/{id}/validate")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'DESIGN')")
	public ResultData<List<WorkflowReview>> validate(@PathVariable String id,
			@RequestParam(defaultValue = "false") boolean ai, Principal p) {
		return ok(service.validate(id, ai, p.getName()));
	}

	@PostMapping("/admin/workflows/{id}/publish")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:publish','workflow:manage') and @workflowSecurity.canDefinition(authentication.name,#id,'PUBLISH')")
	public ResultData<WorkflowDefinition> publish(@PathVariable String id, Principal p) {
		return ok(service.publish(id, p.getName()));
	}

	@GetMapping("/admin/workflow-ai/settings")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage')")
	public ResultData<WorkflowAiSetting> setting() {
		return ok(service.setting());
	}

	@PutMapping("/admin/workflow-ai/settings")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:update','workflow:manage')")
	public ResultData<WorkflowAiSetting> setting(@RequestBody WorkflowAiSetting v, Principal p) {
		return ok(service.updateSetting(v, p.getName()));
	}

	@PostMapping("/admin/workflow-ai/draft")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:create','workflow:manage')")
	public ResultData<Map<String, Object>> draft(@RequestBody Map<String, String> body) {
		return ok(service.aiDraft(body.get("requirement")));
	}

	@GetMapping("/admin/workflow-executors")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:definition:view','workflow:manage')")
	public ResultData<List<ExecutorDescriptor>> executors() {
		return ok(executors.descriptors());
	}

	@PostMapping("/workflows/{id}/start")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:start')")
	public ResultData<WorkflowInstance> start(@PathVariable String id,
			@RequestBody(required = false) Map<String, Object> body, Principal p) {
		Map<String, Object> b = body == null ? Map.of() : body;
		Object raw = b.get("formData");
		Map<String, Object> formData = raw instanceof Map<?, ?> map
				? map.entrySet().stream().collect(
						java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue))
				: Map.of();
		WorkflowIdempotencyService.Reservation reservation = idempotency.reserve(p.getName(), "START:" + id,
				String.valueOf(b.getOrDefault("idempotencyKey", "")));
		if (reservation.completed()) {
			return ok(service.instance(reservation.resourceId()));
		}

		try {
			WorkflowInstance instance = service.start(id, (String) b.get("businessKey"),
					b.get("variablesJson") == null ? "{}" : String.valueOf(b.get("variablesJson")), formData,
					p.getName());
			idempotency.complete(reservation.id(), instance.getId());
			return ok(instance);
		} catch (RuntimeException exception) {
			idempotency.release(reservation.id());
			throw exception;
		}
	}

	@GetMapping("/workflows/available")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:start')")
	public ResultData<List<Map<String, Object>>> available(Principal p) {
		return ok(service.available(p.getName()));
	}

	@GetMapping("/workflows/{id}/start-form")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:start')")
	public ResultData<Map<String, Object>> startForm(@PathVariable String id, Principal p) {
		return ok(service.startForm(id, p.getName()));
	}

	@GetMapping("/workflow-instances/{id}/forms")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view') and @workflowSecurity.canViewInstance(authentication.name,#id)")
	public ResultData<Map<String, Object>> runtimeForms(@PathVariable String id, Principal p) {
		return ok(service.runtimeForms(id, p.getName()));
	}

	@PutMapping("/workflow-instances/{id}/forms/{formId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:task:approve','workflow:task:reject','workflow:instance:start') and @workflowSecurity.canViewInstance(authentication.name,#id)")
	public ResultData<FormInstance> saveRuntimeForm(@PathVariable String id, @PathVariable String formId,
			@RequestBody Map<String, Object> body, Principal p) {
		Object raw = body.get("data");
		Map<String, Object> data = raw instanceof Map<?, ?> map
				? map.entrySet().stream().collect(
						java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue))
				: Map.of();
		return ok(service.saveRuntimeForm(id, formId, data, Boolean.TRUE.equals(body.get("draft")), p.getName()));
	}

	@GetMapping("/workflow-tasks/pending")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<?> pending(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Principal p) {
		return page == null && size == null
				? ok(service.pending(p.getName()))
				: ok(service.pending(p.getName(), page == null ? 0 : page, size == null ? 10 : size));
	}

	@GetMapping("/workflow-tasks/handled")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<?> handled(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Principal p) {
		return page == null && size == null
				? ok(service.handled(p.getName()))
				: ok(service.handled(p.getName(), page == null ? 0 : page, size == null ? 10 : size));
	}

	@GetMapping("/workflow-instances/initiated")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<?> initiated(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Principal p) {
		return page == null && size == null
				? ok(service.initiated(p.getName()))
				: ok(service.initiated(p.getName(), page == null ? 0 : page, size == null ? 10 : size));
	}

	@GetMapping("/workflow-directory/users")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:directory:view')")
	public ResultData<List<Map<String, String>>> directoryUsers(Principal p) {
		return ok(service.directoryUsers(p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/remind")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:remind')")
	public ResultData<WorkflowTask> remind(@PathVariable String id, Principal p) {
		return ok(service.remind(id, p.getName()));
	}

	@PostMapping("/workflow-instances/{id}/remind")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:remind')")
	public ResultData<Integer> remindInstance(@PathVariable String id, Principal p) {
		return ok(service.remindCurrentTasks(id, p.getName()));
	}

	@GetMapping("/workflow-notifications")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<?> notifications(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Principal p) {
		return page == null && size == null
				? ok(notifications.list(p.getName()))
				: ok(notifications.list(p.getName(), page == null ? 0 : page, size == null ? 10 : size));
	}

	@GetMapping("/workflow-notifications/unread-count")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<Long> unreadNotificationCount(Principal p) {
		return ok(notifications.unreadCount(p.getName()));
	}

	@PostMapping("/workflow-notifications/read-all")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<Integer> readAllNotifications(Principal p) {
		return ok(notifications.readAll(p.getName()));
	}

	@PostMapping("/workflow-notifications/{id}/read")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public ResultData<WorkflowNotification> readNotification(@PathVariable String id, Principal p) {
		return ok(notifications.read(id, p.getName()));
	}

	@GetMapping("/admin/workflow-outbox/dead")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:monitor:view','workflow:manage')")
	public ResultData<?> deadOutboxEvents(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null
				? ok(notifications.deadEvents())
				: ok(notifications.deadEvents(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/workflow-outbox/{id}/retry")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:manage','workflow:manage')")
	public ResultData<WorkflowOutbox> retryOutboxEvent(@PathVariable String id, Principal p) {
		return ok(notifications.retryDeadEvent(id, p.getName()));
	}

	@PostMapping("/admin/workflow-outbox/{id}/ignore")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:manage','workflow:manage')")
	public ResultData<WorkflowOutbox> ignoreOutboxEvent(@PathVariable String id, Principal p) {
		return ok(notifications.ignoreDeadEvent(id, p.getName()));
	}

	@GetMapping("/admin/workflow-incidents")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:incident:view','workflow:monitor:view','workflow:manage')")
	public ResultData<?> workflowIncidents(
			@RequestParam(defaultValue = "OPEN") String status,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		// 查询前主动同步一次，管理员无需等待定时扫描周期即可看到刚产生的死信。
		incidents.synchronizeDeadLetters();
		return page == null && size == null
				? ok(incidents.list(status))
				: ok(incidents.list(status, page == null ? 0 : page, size == null ? 10 : size));
	}

	@GetMapping("/admin/workflow-executions")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:incident:view','workflow:monitor:view','workflow:manage')")
	public ResultData<List<WorkflowExecutionLog>> workflowExecutions(
			@RequestParam(required = false) String instanceId) {
		return ok(executionAudit.list(instanceId));
	}

	@PostMapping("/admin/workflow-incidents/{id}/retry")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:incident:manage','workflow:instance:manage','workflow:manage')")
	public ResultData<WorkflowIncident> retryWorkflowIncident(@PathVariable String id, Principal p) {
		return ok(incidents.retry(id, p.getName()));
	}

	@PostMapping("/admin/workflow-incidents/{id}/skip")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:incident:manage','workflow:instance:manage','workflow:manage')")
	public ResultData<WorkflowIncident> skipWorkflowIncident(
			@PathVariable String id,
			@RequestBody(required = false) Map<String, String> body,
			Principal p) {
		Map<String, String> command = body == null ? Map.of() : body;
		return ok(incidents.skip(
				id,
				command.get("targetNodeKey"),
				command.get("reason"),
				p.getName()));
	}

	@PostMapping("/admin/workflow-incidents/{id}/terminate")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:incident:manage','workflow:instance:terminate','workflow:manage')")
	public ResultData<WorkflowIncident> terminateWorkflowIncident(
			@PathVariable String id,
			@RequestBody(required = false) Map<String, String> body,
			Principal p) {
		return ok(incidents.terminate(
				id,
				body == null ? null : body.get("reason"),
				p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/claim")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:claim')")
	public ResultData<WorkflowTask> claim(@PathVariable String id, Principal p) {
		return ok(service.claimTask(id, p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/unclaim")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:claim')")
	public ResultData<WorkflowTask> unclaim(@PathVariable String id, Principal p) {
		return ok(service.unclaimTask(id, p.getName()));
	}

	@GetMapping("/workflow-delegations")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:delegation:manage')")
	public ResultData<?> delegations(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Principal p) {
		return page == null && size == null
				? ok(service.delegations(p.getName()))
				: ok(service.delegations(p.getName(), page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/workflow-delegations")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:delegation:manage')")
	public ResultData<WorkflowDelegation> createDelegation(
			@RequestBody WorkflowDelegation value,
			Principal p) {
		return ok(service.saveDelegation(value, p.getName()));
	}

	@DeleteMapping("/workflow-delegations/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:delegation:manage')")
	public ResultData<Void> deleteDelegation(@PathVariable String id, Principal p) {
		service.deleteDelegation(id, p.getName());
		return ok(null);
	}

	@GetMapping("/admin/workflows/monitor")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:monitor:view','workflow:manage')")
	public ResultData<Map<String, Object>> monitor(Principal p) {
		return ok(service.monitor(p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/transfer")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:transfer')")
	public ResultData<WorkflowTask> transfer(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal p) {
		return ok(service.transferTask(id, body.get("assignee"), body.get("comment"), p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/add-sign")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:add-sign')")
	public ResultData<WorkflowTask> addSign(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal p) {
		return ok(service.addSign(id, body.get("assignee"), body.get("comment"), p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/cc")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:cc')")
	public ResultData<WorkflowTask> cc(@PathVariable String id, @RequestBody Map<String, String> body, Principal p) {
		return ok(service.ccTask(id, body.get("assignee"), body.get("comment"), p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/return")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:return')")
	public ResultData<WorkflowInstance> returnTask(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal p) {
		return ok(service.returnTask(id, body.get("targetNodeKey"), body.get("comment"), p.getName()));
	}

	@PostMapping("/workflow-instances/{id}/withdraw")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:withdraw')")
	public ResultData<WorkflowInstance> withdraw(@PathVariable String id,
			@RequestBody(required = false) Map<String, String> body, Principal p) {
		return ok(service.withdraw(id, body == null ? null : body.get("comment"), p.getName()));
	}

	@PostMapping("/admin/workflow-instances/{id}/terminate")
	@PreAuthorize("@iamAuthorization.any(authentication,'workflow:instance:terminate','workflow:manage') and @workflowSecurity.canManageInstance(authentication.name,#id)")
	public ResultData<WorkflowInstance> terminate(@PathVariable String id,
			@RequestBody(required = false) Map<String, String> body, Principal p) {
		return ok(service.terminate(id, body == null ? null : body.get("reason"), p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/complete")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:approve')")
	public ResultData<WorkflowInstance> complete(@PathVariable String id, @RequestBody Map<String, Object> body,
			Principal p) {
		if (!Boolean.TRUE.equals(body.get("approved")))
			throw new IllegalArgumentException("拒绝任务请使用独立拒绝接口");
		return ok(service.completeTask(id, true, String.valueOf(body.getOrDefault("comment", "")), p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/reject")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:task:reject')")
	public ResultData<WorkflowInstance> reject(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal p) {
		return ok(service.rejectTask(id, body.get("targetNodeKey"), body.get("comment"), p.getName()));
	}

	@PostMapping("/workflow-tasks/{id}/resubmit")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:start')")
	public ResultData<WorkflowInstance> resubmit(@PathVariable String id,
			@RequestBody(required = false) Map<String, String> body, Principal p) {
		return ok(service.resubmitTask(id, body == null ? null : body.get("comment"), p.getName()));
	}
}
