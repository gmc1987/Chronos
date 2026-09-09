package com.chronos.workflow;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.workflow.*;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.model.workflow.*;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.service.iService.IDataScopeService;
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

	public WorkflowSecurityService(IWorkflowDefinitionRepository definitions, IWorkflowDefinitionAclRepository acls,
			IWorkflowInstanceRepository instances, IWorkflowInstanceParticipantRepository participants,
			IWorkflowTaskRepository tasks, IWorkflowNodeRepository nodes, IWorkflowEdgeRepository edges,
			IAdminUserRepository users, IEmployeeAssignmentRepository assignments, IDataScopeService dataScopes,
			WorkflowAssigneeResolver assigneeResolver) {
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
}
