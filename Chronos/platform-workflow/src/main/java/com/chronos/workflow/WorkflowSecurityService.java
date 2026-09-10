package com.chronos.workflow;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.form.IFormInstanceRepository;
import com.chronos.Idao.workflow.*;
import com.chronos.form.FormService;
import com.chronos.model.form.FormField;
import com.chronos.model.form.FormInstance;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.model.workflow.*;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.service.iService.IDataScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("workflowSecurity")
@Transactional(readOnly = true)
public class WorkflowSecurityService {
	private final IWorkflowDefinitionRepository definitions;
	private final IWorkflowDefinitionAclRepository acls;
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowInstanceParticipantRepository participants;
	private final IWorkflowTaskRepository tasks;
	private final IWorkflowNodeRepository nodes;
	private final IWorkflowEdgeRepository edges;
	private final IAdminUserRepository users;
	private final IEmployeeAssignmentRepository assignments;
	private final IDataScopeService dataScopes;
	private final WorkflowAssigneeResolver assigneeResolver;
	private final IFormInstanceRepository formInstances;
	private final FormService formService;
	private final ObjectMapper json = new ObjectMapper();

	public WorkflowSecurityService(IWorkflowDefinitionRepository definitions, IWorkflowDefinitionAclRepository acls,
			IWorkflowInstanceRepository instances, IWorkflowInstanceParticipantRepository participants,
			IWorkflowTaskRepository tasks, IWorkflowNodeRepository nodes, IWorkflowEdgeRepository edges,
			IAdminUserRepository users, IEmployeeAssignmentRepository assignments, IDataScopeService dataScopes,
			WorkflowAssigneeResolver assigneeResolver, IFormInstanceRepository formInstances,
			FormService formService) {
		this.definitions = definitions;
		this.acls = acls;
		this.instances = instances;
		this.participants = participants;
		this.tasks = tasks;
		this.nodes = nodes;
		this.edges = edges;
		this.users = users;
		this.assignments = assignments;
		this.dataScopes = dataScopes;
		this.assigneeResolver = assigneeResolver;
		this.formInstances = formInstances;
		this.formService = formService;
	}

	public boolean canDefinition(String actor, String definitionId, String action) {
		if (actor == null || definitionId == null)
			return false;
		WorkflowDefinition d = definitions.findById(definitionId).orElse(null);
		if (d == null)
			return false;
		if (isSuperAdmin(actor))
			return true;
		String op = normalize(action);
		if (Set.of("VIEW", "DESIGN", "PUBLISH", "MANAGE", "DELETE").contains(op)
				&& (actor.equals(d.getManagerUser()) || actor.equals(d.getCreateBy())))
			return true;
		List<WorkflowDefinitionAcl> rules = acls.findByDefinitionIdAndActionAndEnabledTrue(definitionId, op);
		if (rules.isEmpty() && Set.of("DESIGN", "PUBLISH", "DELETE").contains(op))
			rules = acls.findByDefinitionIdAndActionAndEnabledTrue(definitionId, "MANAGE");
		return rules.stream().anyMatch(r -> matches(actor, r.getSubjectType(), r.getSubjectId()));
	}

	public boolean canViewInstance(String actor, String instanceId) {
		if (actor == null)
			return false;
		WorkflowInstance i = instances.findById(instanceId).orElse(null);
		if (i == null)
			return false;
		if (isSuperAdmin(actor) || actor.equals(i.getInitiator())
				|| participants.existsByInstanceIdAndUsernameAndActiveTrue(instanceId, actor))
			return true;
		if (canDefinition(actor, i.getDefinitionId(), "MANAGE") || canDefinition(actor, i.getDefinitionId(), "VIEW"))
			return true;
		return withinDataScope(actor, i.getInitiator());
	}

	public boolean canStart(String actor, String definitionId) {
		WorkflowDefinition d = definitions.findById(definitionId).orElse(null);
		if (d == null)
			return false;
		List<WorkflowDefinitionAcl> rules = acls.findByDefinitionIdAndActionAndEnabledTrue(definitionId, "START");
		return rules.isEmpty() ? assigneeResolver.canStart(d, actor)
				: rules.stream().anyMatch(r -> matches(actor, r.getSubjectType(), r.getSubjectId()));
	}

	public boolean canRemind(String actor, String taskId) {
		WorkflowTask t = tasks.findById(taskId).orElse(null);
		if (t == null)
			return false;
		WorkflowInstance i = instances.findById(t.getInstanceId()).orElse(null);
		return i != null && (actor.equals(i.getInitiator()) || canDefinition(actor, i.getDefinitionId(), "MANAGE"));
	}

	public boolean canManageInstance(String actor, String instanceId) {
		WorkflowInstance i = instances.findById(instanceId).orElse(null);
		return i != null && canDefinition(actor, i.getDefinitionId(), "MANAGE");
	}

	public boolean canViewDirectoryUser(String actor, String target) {
		return actor != null && target != null
				&& (actor.equals(target) || isSuperAdmin(actor) || withinDataScope(actor, target));
	}

	public boolean canEditCurrentTask(String actor, String instanceId) {
		return tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId).stream()
				.anyMatch(t -> "PENDING".equals(t.getStatus()) && actor.equals(t.getAssignee()));
	}

	/**
	 * 当前任务处理人只有在节点把至少一个 FILE 字段声明为 EDIT 时才可以
	 * 直接向流程实例上传附件，避免绕过动态表单制造不可见的绑定文件。
	 */
	public boolean canEditAnyFileField(String actor, String instanceId) {
		if (!canEditCurrentTask(actor, instanceId)) {
			return false;
		}
		WorkflowContext context = workflowContext(instanceId);
		if (context == null) {
			return false;
		}
		Map<String, String> permissions = fieldPermissions(context.node());
		for (String formId : context.formIds()) {
			for (FormField field : formService.fields(formId)) {
				if ("FILE".equals(field.getFieldType())
						&& "EDIT".equals(permissions.get(formId + "." + field.getFieldKey()))) {
					return true;
				}
			}
		}
		return false;
	}

	/** 当前节点必须对实际引用该文件的 FILE 字段拥有 EDIT 权限。 */
	public boolean canEditFile(String actor, String instanceId, String fileId) {
		if (!canEditCurrentTask(actor, instanceId)) {
			return false;
		}
		WorkflowContext context = workflowContext(instanceId);
		if (context == null) {
			return false;
		}
		Map<String, String> permissions = fieldPermissions(context.node());
		for (FormInstance form : formInstances.findByWorkflowInstanceIdOrderByCreateTimeAsc(instanceId)) {
			if (!context.formIds().contains(form.getFormId())) {
				continue;
			}
			JsonNode data = readJson(form.getDataJson());
			for (FormField field : formService.fields(form.getFormId())) {
				String permissionKey = form.getFormId() + "." + field.getFieldKey();
				if (!"FILE".equals(field.getFieldType())
						|| !"EDIT".equals(permissions.get(permissionKey))) {
					continue;
				}
				for (JsonNode attachment : data.path(field.getFieldKey())) {
					if (fileId.equals(attachment.path("id").asText())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean canNode(String actor, String nodeId, String action) {
		return nodeId != null
				&& nodes.findById(nodeId).map(n -> canDefinition(actor, n.getFlowId(), action)).orElse(false);
	}

	public boolean canEdge(String actor, String edgeId, String action) {
		return edgeId != null
				&& edges.findById(edgeId).map(e -> canDefinition(actor, e.getFlowId(), action)).orElse(false);
	}

	public boolean canAcl(String actor, String aclId, String action) {
		return aclId != null
				&& acls.findById(aclId).map(a -> canDefinition(actor, a.getDefinitionId(), action)).orElse(false);
	}

	public void requireDefinition(String actor, String definitionId, String action) {
		if (!canDefinition(actor, definitionId, action))
			throw new AccessDeniedException("无权执行该流程定义操作");
	}

	public void requireInstanceView(String actor, String instanceId) {
		if (!canViewInstance(actor, instanceId))
			throw new AccessDeniedException("无权查看该流程实例");
	}

	public void requireCurrentTask(String actor, String instanceId) {
		if (!canEditCurrentTask(actor, instanceId))
			throw new AccessDeniedException("仅当前任务处理人可修改表单");
	}

	private WorkflowContext workflowContext(String instanceId) {
		WorkflowInstance instance = instances.findById(instanceId).orElse(null);
		if (instance == null) {
			return null;
		}
		WorkflowDefinition definition = definitions.findById(instance.getDefinitionId()).orElse(null);
		if (definition == null) {
			return null;
		}
		WorkflowNode node = nodes.findByFlowIdAndNodeKey(
				definition.getId(),
				instance.getCurrentNodeKey()).orElse(null);
		if (node == null) {
			return null;
		}
		Set<String> formIds = new LinkedHashSet<>();
		if (definition.getMainFormId() != null && !definition.getMainFormId().isBlank()) {
			formIds.add(definition.getMainFormId());
		}
		JsonNode additionalForms = readJson(node.getAdditionalFormIds());
		if (additionalForms.isArray()) {
			for (JsonNode formId : additionalForms) {
				if (!formId.asText().isBlank()) {
					formIds.add(formId.asText());
				}
			}
		}
		return new WorkflowContext(node, List.copyOf(formIds));
	}

	private Map<String, String> fieldPermissions(WorkflowNode node) {
		Map<String, String> permissions = new HashMap<>();
		readJson(node.getFieldPermissionsJson())
				.path("permissions")
				.fields()
				.forEachRemaining(entry -> permissions.put(
						entry.getKey(),
						entry.getValue().asText()));
		return permissions;
	}

	private JsonNode readJson(String value) {
		try {
			return json.readTree(value == null || value.isBlank() ? "{}" : value);
		} catch (Exception exception) {
			return json.createObjectNode();
		}
	}

	private boolean matches(String actor, String type, String id) {
		if ("ALL".equalsIgnoreCase(type))
			return "*".equals(id) || "ALL".equalsIgnoreCase(id);
		AdminUser u = users.findByUsername(actor);
		if (u == null)
			return false;
		return switch (normalize(type)) {
		case "USER" -> actor.equals(id);
		case "ROLE" -> u.getRoles().stream().filter(r -> Integer.valueOf(1).equals(r.getStatus()))
				.map(Role::getRoleCode).anyMatch(id::equals);
		case "ORGANIZATION", "DEPARTMENT", "POSITION" -> {
			if (u.getEmployeeId() == null)
				yield false;
			var a = assignments.findCurrentPrimaryAssignment(u.getEmployeeId(), LocalDate.now());
			yield a.map(x -> switch (normalize(type)) {
			case "ORGANIZATION" -> id.equals(x.getOrganizationId());
			case "DEPARTMENT" -> id.equals(x.getOrganizationUnitId());
			default -> id.equals(x.getPositionId());
			}).orElse(false);
		}
		default -> false;
		};
	}

	private boolean withinDataScope(String viewer, String owner) {
		try {
			DataScopeContext scope = dataScopes.resolve(viewer);
			if (scope.fullAccess())
				return true;
			AdminUser target = users.findByUsername(owner);
			if (target == null || target.getEmployeeId() == null)
				return false;
			if (scope.employeeIds().contains(target.getEmployeeId()))
				return true;
			var a = assignments.findCurrentPrimaryAssignment(target.getEmployeeId(), LocalDate.now());
			return a.map(x -> scope.organizationIds().contains(x.getOrganizationId())
					|| scope.organizationUnitIds().contains(x.getOrganizationUnitId())).orElse(false);
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isSuperAdmin(String actor) {
		AdminUser u = users.findByUsername(actor);
		return u != null && u.getRoles().stream().anyMatch(
				r -> Integer.valueOf(1).equals(r.getStatus()) && "SUPER_ADMIN".equalsIgnoreCase(r.getRoleCode()));
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase();
	}

	private record WorkflowContext(WorkflowNode node, List<String> formIds) {
	}
}
