package com.chronos.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.Idao.form.IFormInstanceRevisionRepository;
import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowEdgeRepository;
import com.chronos.Idao.workflow.IWorkflowExecutionLogRepository;
import com.chronos.Idao.workflow.IWorkflowIncidentRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceParticipantRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.form.FormService;
import com.chronos.model.form.FormInstance;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exports a terminal workflow as a consistent recovery package. Export never
 * deletes Chronos rows or Flowable history; physical retention/restore belongs
 * to the deployment backup procedure.
 */
@Service
public class WorkflowArchiveService {
	private static final Set<String> TERMINAL = Set.of(
			"COMPLETED", "REJECTED", "WITHDRAWN", "TERMINATED");
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowDefinitionRepository definitions;
	private final IWorkflowNodeRepository nodes;
	private final IWorkflowEdgeRepository edges;
	private final IWorkflowTaskRepository tasks;
	private final IFormInstanceRepository formInstances;
	private final IFormInstanceRevisionRepository formRevisions;
	private final IFormDefinitionRepository formDefinitions;
	private final FormService forms;
	private final IWorkflowExecutionLogRepository executionLogs;
	private final IWorkflowIncidentRepository incidents;
	private final IWorkflowInstanceParticipantRepository participants;
	private final RuntimeService runtime;
	private final IAuditLogService audit;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

	public WorkflowArchiveService(
			IWorkflowInstanceRepository instances,
			IWorkflowDefinitionRepository definitions,
			IWorkflowNodeRepository nodes,
			IWorkflowEdgeRepository edges,
			IWorkflowTaskRepository tasks,
			IFormInstanceRepository formInstances,
			IFormInstanceRevisionRepository formRevisions,
			IFormDefinitionRepository formDefinitions,
			FormService forms,
			IWorkflowExecutionLogRepository executionLogs,
			IWorkflowIncidentRepository incidents,
			IWorkflowInstanceParticipantRepository participants,
			RuntimeService runtime,
			IAuditLogService audit) {
		this.instances = instances;
		this.definitions = definitions;
		this.nodes = nodes;
		this.edges = edges;
		this.tasks = tasks;
		this.formInstances = formInstances;
		this.formRevisions = formRevisions;
		this.formDefinitions = formDefinitions;
		this.forms = forms;
		this.executionLogs = executionLogs;
		this.incidents = incidents;
		this.participants = participants;
		this.runtime = runtime;
		this.audit = audit;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public ArchivePackage export(String instanceId, String actor) {
		WorkflowInstance instance = instances.findById(instanceId)
				.orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
		if (!TERMINAL.contains(instance.getStatus())) {
			throw new IllegalArgumentException("只能归档已结束的流程实例");
		}
		boolean hasOpenTasks = tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId)
				.stream()
				.anyMatch(task -> Set.of("PENDING", "CLAIMABLE", "WAITING")
						.contains(task.getStatus()));
		if (hasOpenTasks) {
			throw new IllegalStateException("流程仍存在未结束任务，归档中止");
		}
		boolean hasOpenIncidents = incidents.findByInstanceIdOrderByCreateTimeAsc(instanceId)
				.stream()
				.anyMatch(incident -> "OPEN".equals(incident.getStatus()));
		if (hasOpenIncidents) {
			throw new IllegalStateException("流程仍存在待处理事故，归档中止");
		}
		if (instance.getEngineInstanceId() != null
				&& !instance.getEngineInstanceId().isBlank()
				&& runtime.createProcessInstanceQuery()
						.processInstanceId(instance.getEngineInstanceId())
						.count() > 0) {
			throw new IllegalStateException("Flowable 运行实例仍存在，不能归档");
		}
		var definition = definitions.findById(instance.getDefinitionId())
				.orElseThrow(() -> new IllegalStateException("流程定义不存在，归档中止"));
		List<WorkflowNode> definitionNodes = nodes.findByFlowIdOrderByCreateTimeAsc(definition.getId());
		List<FormInstance> savedForms = formInstances.findByWorkflowInstanceIdOrderByCreateTimeAsc(instanceId);
		Map<String, Object> formSchemas = new LinkedHashMap<>();
		if (definition.getMainFormId() != null && !definition.getMainFormId().isBlank()) {
			includeForm(formSchemas, definition.getMainFormId());
		}
		for (FormInstance saved : savedForms) {
			includeForm(formSchemas, saved.getFormId());
		}
		for (WorkflowNode node : definitionNodes) {
			includeAdditionalForms(formSchemas, node);
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("schemaVersion", 1);
		payload.put("instance", instance);
		payload.put("definition", definition);
		payload.put("nodes", definitionNodes);
		payload.put("edges", edges.findByFlowIdOrderByCreateTimeAsc(definition.getId()));
		payload.put("formSchemas", formSchemas);
		payload.put("formInstances", savedForms);
		payload.put("formRevisions", formRevisions.findByWorkflowInstanceIdOrderByCreateTimeAsc(instanceId));
		payload.put("tasks", tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId));
		payload.put("participants", participants.findByInstanceIdOrderByCreateTimeAsc(instanceId));
		payload.put("executionLogs", executionLogs.findByInstanceIdOrderByCreateTimeAsc(instanceId));
		payload.put("incidents", incidents.findByInstanceIdOrderByCreateTimeAsc(instanceId));
		try {
			String payloadJson = json.writeValueAsString(payload);
			audit.log(actor, "WORKFLOW_ARCHIVE_EXPORT", "instanceId=" + instanceId);
			return new ArchivePackage(1, instanceId, sha256(payloadJson), payloadJson);
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("流程归档包序列化失败", exception);
		}
	}

	private void includeForm(Map<String, Object> target, String id) {
		if (target.containsKey(id)) {
			return;
		}
		var definition = formDefinitions.findById(id)
				.orElseThrow(() -> new IllegalStateException("关联表单定义不存在，归档中止"));
		target.put(id, Map.of("definition", definition, "fields", forms.fields(id)));
	}

	private void includeAdditionalForms(Map<String, Object> target, WorkflowNode node) {
		String raw = node.getAdditionalFormIds();
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			var ids = json.readTree(raw);
			if (!ids.isArray()) {
				throw new IllegalStateException("节点附加表单配置不是数组");
			}
			for (var id : ids) {
				includeForm(target, id.asText());
			}
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("节点附加表单配置无法解析，归档中止", exception);
		}
	}

	private String sha256(String source) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(source.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 不可用", exception);
		}
	}

	public record ArchivePackage(
			int schemaVersion,
			String instanceId,
			String sha256,
			String payloadJson) {
	}
}
