package com.chronos.workflow;

import com.chronos.Idao.workflow.*;
import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.form.FormService;
import com.chronos.file.service.ManagedFileService;
import com.chronos.model.form.*;
import com.chronos.model.workflow.*;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.ai.WorkflowAiProvider;
import com.chronos.workflow.executor.WorkflowExecutorRegistry;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
	private static final Set<String> NODE_TYPES = Set.of("START", "APPROVAL", "TASK", "CONDITION", "CC", "END",
			"SERVICE_TASK", "HTTP_TASK", "AGENT_TASK", "MESSAGE_TASK");
	private static final Pattern CONDITION = Pattern.compile("^([A-Za-z][A-Za-z0-9_.]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
	private final IWorkflowDefinitionRepository definitions;
	private final IWorkflowNodeRepository nodes;
	private final IWorkflowEdgeRepository edges;
	private final IWorkflowAiSettingRepository settings;
	private final IWorkflowReviewRepository reviews;
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowTaskRepository tasks;
	private final List<WorkflowAiProvider> aiProviders;
	private final IFormDefinitionRepository formDefinitions;
	private final FlowableDeploymentService flowableDeployment;
	private final FlowableRuntimeCoordinator flowableRuntime;
	private final FormService formService;
	private final ManagedFileService managedFiles;
	private final WorkflowAssigneeResolver assigneeResolver;
	private final WorkflowSecurityService security;
	private final IWorkflowDefinitionAclRepository acls;
	private final IWorkflowInstanceParticipantRepository participants;
	private final IWorkflowTaskCandidateRepository candidates;
	private final IWorkflowDelegationRepository delegations;
	private final IWorkflowIncidentRepository incidents;
	private final WorkflowSlaService sla;
	private final WorkflowExecutorRegistry executorRegistry;
	private final IAuditLogService audit;
	private final ApplicationEventPublisher eventPublisher;
	private final List<WorkflowStartValidator> startValidators;
	private final ObjectMapper json = new ObjectMapper();

	public WorkflowService(IWorkflowDefinitionRepository definitions, IWorkflowNodeRepository nodes,
			IWorkflowEdgeRepository edges, IWorkflowAiSettingRepository settings, IWorkflowReviewRepository reviews,
			IWorkflowInstanceRepository instances, IWorkflowTaskRepository tasks, List<WorkflowAiProvider> aiProviders,
			IAuditLogService audit, IFormDefinitionRepository formDefinitions,
			FlowableDeploymentService flowableDeployment, FlowableRuntimeCoordinator flowableRuntime,
			FormService formService, ManagedFileService managedFiles,
			WorkflowAssigneeResolver assigneeResolver,
			WorkflowExecutorRegistry executorRegistry, WorkflowSecurityService security,
			IWorkflowDefinitionAclRepository acls, IWorkflowInstanceParticipantRepository participants,
			IWorkflowTaskCandidateRepository candidates, IWorkflowDelegationRepository delegations,
			IWorkflowIncidentRepository incidents,
			WorkflowSlaService sla,
			ApplicationEventPublisher eventPublisher,
			List<WorkflowStartValidator> startValidators) {
		this.definitions = definitions;
		this.nodes = nodes;
		this.edges = edges;
		this.settings = settings;
		this.reviews = reviews;
		this.instances = instances;
		this.tasks = tasks;
		this.aiProviders = aiProviders;
		this.audit = audit;
		this.formDefinitions = formDefinitions;
		this.flowableDeployment = flowableDeployment;
		this.flowableRuntime = flowableRuntime;
		this.formService = formService;
		this.managedFiles = managedFiles;
		this.assigneeResolver = assigneeResolver;
		this.executorRegistry = executorRegistry;
		this.security = security;
		this.acls = acls;
		this.participants = participants;
		this.candidates = candidates;
		this.delegations = delegations;
		this.incidents = incidents;
		this.sla = sla;
		this.eventPublisher = eventPublisher;
		this.startValidators = startValidators;
	}

	@Transactional(readOnly = true)
	public Page<WorkflowDefinition> list(Pageable pageable, String actor) {
		List<WorkflowDefinition> allowed = definitions.findAll(pageable.getSort()).stream()
				.filter(d -> security.canDefinition(actor, d.getId(), "VIEW")).toList();
		int from = Math.min((int) pageable.getOffset(), allowed.size()),
				to = Math.min(from + pageable.getPageSize(), allowed.size());
		return new PageImpl<>(allowed.subList(from, to), pageable, allowed.size());
	}

	@Transactional(readOnly = true)
	public WorkflowDefinition get(String id) {
		return requireDefinition(id);
	}

	@Transactional(readOnly = true)
	public WorkflowInstance instance(String id) {
		return requireInstance(id);
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> available(String actor) {
		Map<String, WorkflowDefinition> latestByFlowCode = new HashMap<>();
		for (WorkflowDefinition definition : definitions.findByStatusOrderByFlowNameAsc("PUBLISHED")) {
			// 门户只能展示已经部署到 Flowable、可以真正发起的版本。
			if (definition.getFlowableProcessKey() == null
					|| definition.getFlowableProcessKey().isBlank()
					|| !security.canStart(actor, definition.getId())) {
				continue;
			}

			// 同一流程编码只展示最近发布版本，避免用户误发起历史版本。
			latestByFlowCode.merge(
					definition.getFlowCode(),
					definition,
					this::newerPublishedDefinition);
		}

		return latestByFlowCode.values().stream()
				.sorted(Comparator.comparing(
						WorkflowDefinition::getFlowName,
						Comparator.nullsLast(String::compareTo)))
				.map(this::definitionView)
				.toList();
	}

	private WorkflowDefinition newerPublishedDefinition(
			WorkflowDefinition left,
			WorkflowDefinition right) {
		Comparator<WorkflowDefinition> comparator = Comparator
				.comparing(
						WorkflowDefinition::getPublishedAt,
						Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(
						WorkflowDefinition::getCreateTime,
						Comparator.nullsFirst(Comparator.naturalOrder()));
		return comparator.compare(left, right) >= 0 ? left : right;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> startForm(String flowId, String actor) {
		WorkflowDefinition d = requireDefinition(flowId);
		if (!"PUBLISHED".equals(d.getStatus()))
			throw new IllegalArgumentException("流程未发布");
		if (!security.canStart(actor, flowId))
			throw new AccessDeniedException("不在该流程的发起范围内");
		if (d.getMainFormId() == null || d.getMainFormId().isBlank())
			return Map.of("flowId", d.getId(), "flowName", d.getFlowName(), "fields", List.of());
		FormDefinition form = formService.definition(d.getMainFormId());
		List<Map<String, Object>> schema = new ArrayList<>();
		for (FormField field : formService.fields(form.getId()))
			schema.add(Map.of("fieldKey", field.getFieldKey(), "fieldLabel", field.getFieldLabel(), "fieldType",
					field.getFieldType(), "required", Boolean.TRUE.equals(field.getRequired()), "permission", "EDIT",
					"optionsJson", field.getOptionsJson() == null ? "[]" : field.getOptionsJson()));
		return Map.of("flowId", d.getId(), "flowName", d.getFlowName(), "description",
				d.getDescription() == null ? "" : d.getDescription(), "formId", form.getId(), "formName",
				form.getFormName(), "fields", schema);
	}

	@Transactional
	public WorkflowDefinition save(WorkflowDefinition value, String actor) {
		if (value.getFlowName() == null || value.getFlowName().isBlank())
			throw new IllegalArgumentException("流程名称不能为空");
		value.setFlowCode(required(value.getFlowCode(), "流程编码不能为空"));
		if (value.getVersion() == null || value.getVersion().isBlank())
			value.setVersion("v1");
		if (definitions.existsByFlowCodeAndVersion(value.getFlowCode(), value.getVersion()))
			throw new IllegalArgumentException("流程编码和版本已存在");
		validateJson(value.getStarterScopeJson(), "发起范围");
		validateForm(value.getMainFormId());
		value.setStatus("DRAFT");
		value.setPublishedAt(null);
		WorkflowDefinition saved = definitions.save(value);
		addAcl(saved.getId(), "USER", actor, "MANAGE");
		audit.log(actor, "WORKFLOW_DEFINITION_SAVE", "flowId=" + saved.getId());
		return saved;
	}

	@Transactional
	public WorkflowDefinition update(WorkflowDefinition value, String actor) {
		WorkflowDefinition old = requireDefinition(value.getId());
		ensureDraft(old);
		old.setFlowName(required(value.getFlowName(), "流程名称不能为空"));
		old.setDescription(value.getDescription());
		old.setCategory(value.getCategory());
		old.setEntryNodeKey(value.getEntryNodeKey());
		old.setTags(value.getTags());
		old.setConfigJson(value.getConfigJson());
		old.setManagerUser(value.getManagerUser());
		validateJson(value.getStarterScopeJson(), "发起范围");
		old.setStarterScopeJson(value.getStarterScopeJson());
		validateForm(value.getMainFormId());
		old.setMainFormId(value.getMainFormId());
		old.setAiAssistEnabled(Boolean.TRUE.equals(value.getAiAssistEnabled()));
		audit.log(actor, "WORKFLOW_DEFINITION_UPDATE", "flowId=" + old.getId());
		return definitions.save(old);
	}

	@Transactional
	public void delete(String id, String actor) {
		WorkflowDefinition d = requireDefinition(id);
		if ("PUBLISHED".equals(d.getStatus()))
			throw new IllegalArgumentException("已发布流程不可删除，请停用或创建新版本");
		reviews.deleteByFlowId(id);
		edges.deleteByFlowId(id);
		nodes.deleteByFlowId(id);
		acls.deleteByDefinitionId(id);
		definitions.delete(d);
		audit.log(actor, "WORKFLOW_DEFINITION_DELETE", "flowId=" + id);
	}

	@Transactional
	public WorkflowDefinition disable(String id, String actor) {
		WorkflowDefinition d = requireDefinition(id);
		if (!"PUBLISHED".equals(d.getStatus()))
			throw new IllegalArgumentException("只有已发布流程可以停用");
		d.setStatus("DISABLED");
		audit.log(actor, "WORKFLOW_DISABLE", "flowId=" + id);
		return definitions.save(d);
	}

	@Transactional
	public WorkflowDefinition createVersion(String id, String version, String actor) {
		WorkflowDefinition source = requireDefinition(id);
		String next = required(version, "新版本号不能为空");
		if (definitions.existsByFlowCodeAndVersion(source.getFlowCode(), next))
			throw new IllegalArgumentException("该版本已存在");
		WorkflowDefinition d = new WorkflowDefinition();
		d.setFlowCode(source.getFlowCode());
		d.setFlowName(source.getFlowName());
		d.setCategory(source.getCategory());
		d.setVersion(next);
		d.setDescription(source.getDescription());
		d.setEntryNodeKey(source.getEntryNodeKey());
		d.setStatus("DRAFT");
		d.setTags(source.getTags());
		d.setConfigJson(source.getConfigJson());
		d.setMainFormId(source.getMainFormId());
		d.setManagerUser(source.getManagerUser());
		d.setStarterScopeJson(source.getStarterScopeJson());
		d.setAiAssistEnabled(source.getAiAssistEnabled());
		d = definitions.save(d);
		for (WorkflowDefinitionAcl a : acls.findByDefinitionIdOrderByActionAscSubjectTypeAscSubjectIdAsc(id))
			addAcl(d.getId(), a.getSubjectType(), a.getSubjectId(), a.getAction());
		for (WorkflowNode n : nodes(id)) {
			WorkflowNode copy = new WorkflowNode();
			copy.setFlowId(d.getId());
			copy.setNodeKey(n.getNodeKey());
			copy.setNodeName(n.getNodeName());
			copy.setNodeType(n.getNodeType());
			copy.setExecutor(n.getExecutor());
			copy.setTimeoutSec(n.getTimeoutSec());
			copy.setRetryMax(n.getRetryMax());
			copy.setRetryIntervalSec(n.getRetryIntervalSec());
			copy.setInputSchema(n.getInputSchema());
			copy.setOutputSchema(n.getOutputSchema());
			copy.setPropertiesJson(n.getPropertiesJson());
			copy.setAdditionalFormIds(n.getAdditionalFormIds());
			copy.setFieldPermissionsJson(n.getFieldPermissionsJson());
			nodes.save(copy);
		}
		for (WorkflowEdge e : edges(id)) {
			WorkflowEdge copy = new WorkflowEdge();
			copy.setFlowId(d.getId());
			copy.setFromNodeKey(e.getFromNodeKey());
			copy.setToNodeKey(e.getToNodeKey());
			copy.setConditionExpr(e.getConditionExpr());
			copy.setIsDefault(e.getIsDefault());
			edges.save(copy);
		}
		audit.log(actor, "WORKFLOW_CREATE_VERSION", "source=" + id + ", target=" + d.getId() + ", version=" + next);
		return d;
	}

	@Transactional(readOnly = true)
	public List<WorkflowDefinitionAcl> acls(String definitionId) {
		requireDefinition(definitionId);
		return acls.findByDefinitionIdOrderByActionAscSubjectTypeAscSubjectIdAsc(definitionId);
	}

	@Transactional
	public WorkflowDefinitionAcl saveAcl(WorkflowDefinitionAcl value, String actor) {
		requireDefinition(value.getDefinitionId());
		String type = upper(value.getSubjectType()), action = upper(value.getAction());
		if (!Set.of("ALL", "USER", "ROLE", "ORGANIZATION", "DEPARTMENT", "POSITION").contains(type))
			throw new IllegalArgumentException("不支持的ACL主体类型");
		if (!Set.of("START", "VIEW", "DESIGN", "PUBLISH", "DELETE", "MANAGE").contains(action))
			throw new IllegalArgumentException("不支持的ACL操作");
		WorkflowDefinitionAcl saved = addAcl(value.getDefinitionId(), type, value.getSubjectId(), action);
		saved.setEnabled(value.getEnabled() == null || value.getEnabled());
		audit.log(actor, "WORKFLOW_ACL_SAVE", "flowId=" + value.getDefinitionId() + ", action=" + action + ", subject="
				+ type + ":" + value.getSubjectId());
		return acls.save(saved);
	}

	@Transactional
	public void deleteAcl(String id, String actor) {
		WorkflowDefinitionAcl acl = acls.findById(id).orElseThrow(() -> new IllegalArgumentException("ACL不存在"));
		acls.delete(acl);
		audit.log(actor, "WORKFLOW_ACL_DELETE", "flowId=" + acl.getDefinitionId() + ", aclId=" + id);
	}

	@Transactional(readOnly = true)
	public List<WorkflowNode> nodes(String flowId) {
		requireDefinition(flowId);
		return nodes.findByFlowIdOrderByCreateTimeAsc(flowId);
	}

	@Transactional(readOnly = true)
	public List<WorkflowEdge> edges(String flowId) {
		requireDefinition(flowId);
		return edges.findByFlowIdOrderByCreateTimeAsc(flowId);
	}

	@Transactional
	public WorkflowNode saveNode(WorkflowNode value) {
		WorkflowDefinition d = requireDefinition(value.getFlowId());
		ensureDraft(d);
		validateNode(value);
		return nodes.save(value);
	}

	@Transactional
	public WorkflowNode updateNode(WorkflowNode value) {
		WorkflowNode old = nodes.findById(value.getId()).orElseThrow(() -> new IllegalArgumentException("节点不存在"));
		ensureDraft(requireDefinition(old.getFlowId()));
		value.setFlowId(old.getFlowId());
		validateNode(value);
		old.setNodeKey(value.getNodeKey());
		old.setNodeName(value.getNodeName());
		old.setNodeType(value.getNodeType());
		old.setExecutor(value.getExecutor());
		old.setTimeoutSec(value.getTimeoutSec());
		old.setRetryMax(value.getRetryMax());
		old.setRetryIntervalSec(value.getRetryIntervalSec());
		old.setInputSchema(value.getInputSchema());
		old.setOutputSchema(value.getOutputSchema());
		old.setPropertiesJson(value.getPropertiesJson());
		old.setAdditionalFormIds(value.getAdditionalFormIds());
		old.setFieldPermissionsJson(value.getFieldPermissionsJson());
		return nodes.save(old);
	}

	@Transactional
	public void deleteNode(String id) {
		WorkflowNode n = nodes.findById(id).orElseThrow(() -> new IllegalArgumentException("节点不存在"));
		ensureDraft(requireDefinition(n.getFlowId()));
		edges.findByFlowIdOrderByCreateTimeAsc(n.getFlowId()).stream()
				.filter(e -> n.getNodeKey().equals(e.getFromNodeKey()) || n.getNodeKey().equals(e.getToNodeKey()))
				.forEach(edges::delete);
		nodes.delete(n);
	}

	@Transactional
	public WorkflowEdge saveEdge(WorkflowEdge value) {
		ensureDraft(requireDefinition(value.getFlowId()));
		validateEdge(value);
		return edges.save(value);
	}

	@Transactional
	public WorkflowEdge updateEdge(WorkflowEdge value) {
		WorkflowEdge old = edges.findById(value.getId()).orElseThrow(() -> new IllegalArgumentException("连线不存在"));
		ensureDraft(requireDefinition(old.getFlowId()));
		value.setFlowId(old.getFlowId());
		validateEdge(value);
		old.setFromNodeKey(value.getFromNodeKey());
		old.setToNodeKey(value.getToNodeKey());
		old.setConditionExpr(value.getConditionExpr());
		old.setIsDefault(Boolean.TRUE.equals(value.getIsDefault()));
		return edges.save(old);
	}

	@Transactional
	public void deleteEdge(String id) {
		WorkflowEdge e = edges.findById(id).orElseThrow(() -> new IllegalArgumentException("连线不存在"));
		ensureDraft(requireDefinition(e.getFlowId()));
		edges.delete(e);
	}

	@Transactional
	public List<WorkflowReview> validate(String flowId, boolean includeAi, String actor) {
		WorkflowDefinition d = requireDefinition(flowId);
		List<WorkflowNode> ns = nodes(flowId);
		List<WorkflowEdge> es = edges(flowId);
		reviews.deleteByFlowId(flowId);
		List<WorkflowReview> result = new ArrayList<>();
		Map<String, WorkflowNode> byKey = new LinkedHashMap<>();
		for (WorkflowNode n : ns) {
			byKey.put(n.getNodeKey(), n);
			String type = upper(n.getNodeType());
			if (!NODE_TYPES.contains(type))
				result.add(finding(flowId, n.getId(), "RULE", "HIGH", "INVALID_NODE_TYPE", "不支持的节点类型",
						"允许类型：" + NODE_TYPES, "修改节点类型", true, d, ns, es));
			if (Set.of("APPROVAL", "TASK").contains(type) && !hasAssignee(n))
				result.add(finding(flowId, n.getId(), "RULE", "HIGH", "ASSIGNEE_MISSING", "处理人规则缺失", "人工节点必须配置处理人规则",
						"配置处理人", true, d, ns, es));
			if (Set.of("SERVICE_TASK", "HTTP_TASK", "AGENT_TASK", "MESSAGE_TASK").contains(type)
					&& (n.getExecutor() == null || n.getExecutor().isBlank()))
				result.add(finding(flowId, n.getId(), "RULE", "HIGH", "EXECUTOR_MISSING", "自动节点执行器未配置", "自动节点尚不能运行",
						"接通并选择可用执行器", true, d, ns, es));
			if (Set.of("SERVICE_TASK", "HTTP_TASK", "AGENT_TASK", "MESSAGE_TASK").contains(type)
					&& n.getExecutor() != null
					&& !n.getExecutor().isBlank()) {
				String executorError = executorRegistry.configurationError(n);
				if (executorError != null) {
					result.add(finding(
							flowId,
							n.getId(),
							"RULE",
							"HIGH",
							"EXECUTOR_INVALID",
							"自动节点配置不可执行",
							executorError,
							"完善执行器配置或联系运维启用",
							true,
							d,
							ns,
							es));
				}
			}
		}
		if (ns.isEmpty())
			result.add(finding(flowId, null, "RULE", "HIGH", "EMPTY_FLOW", "流程没有节点", "至少需要开始和结束节点", "添加节点", true, d, ns,
					es));
		long starts = ns.stream().filter(n -> "START".equals(upper(n.getNodeType()))).count(),
				ends = ns.stream().filter(n -> "END".equals(upper(n.getNodeType()))).count();
		if (starts != 1)
			result.add(finding(flowId, null, "RULE", "HIGH", "START_COUNT", "开始节点数量不正确", "必须且只能有一个开始节点", "保留一个开始节点",
					true, d, ns, es));
		if (ends < 1)
			result.add(finding(flowId, null, "RULE", "HIGH", "END_MISSING", "缺少结束节点", "至少需要一个结束节点", "添加结束节点", true, d,
					ns, es));
		if (d.getEntryNodeKey() == null || !byKey.containsKey(d.getEntryNodeKey()))
			result.add(finding(flowId, null, "RULE", "HIGH", "ENTRY_MISSING", "入口节点无效", "入口节点必须指向现有节点", "设置入口节点", true,
					d, ns, es));
		for (WorkflowEdge e : es) {
			if (!byKey.containsKey(e.getFromNodeKey()) || !byKey.containsKey(e.getToNodeKey()))
				result.add(finding(flowId, null, "RULE", "HIGH", "BROKEN_EDGE", "连线引用了不存在的节点",
						e.getFromNodeKey() + " -> " + e.getToNodeKey(), "重新连接节点", true, d, ns, es));
			if (byKey.containsKey(e.getToNodeKey()) && "START".equals(upper(byKey.get(e.getToNodeKey()).getNodeType())))
				result.add(finding(flowId, null, "RULE", "HIGH", "START_INCOMING", "开始节点不能有入线",
						e.getFromNodeKey() + " -> " + e.getToNodeKey(), "移除连线", true, d, ns, es));
			if (byKey.containsKey(e.getFromNodeKey())
					&& "END".equals(upper(byKey.get(e.getFromNodeKey()).getNodeType())))
				result.add(finding(flowId, null, "RULE", "HIGH", "END_OUTGOING", "结束节点不能有出线",
						e.getFromNodeKey() + " -> " + e.getToNodeKey(), "移除连线", true, d, ns, es));
			if (e.getConditionExpr() != null && !e.getConditionExpr().isBlank()
					&& !CONDITION.matcher(e.getConditionExpr().trim()).matches())
				result.add(finding(flowId, null, "RULE", "HIGH", "INVALID_CONDITION", "条件表达式不合法", e.getConditionExpr(),
						"使用 field >= value 格式", true, d, ns, es));
		}
		Map<String, List<WorkflowEdge>> outgoing = es.stream()
				.collect(java.util.stream.Collectors.groupingBy(WorkflowEdge::getFromNodeKey));
		for (WorkflowNode n : ns)
			if (!"END".equals(upper(n.getNodeType())) && outgoing.getOrDefault(n.getNodeKey(), List.of()).isEmpty())
				result.add(finding(flowId, n.getId(), "RULE", "HIGH", "DEAD_END", "节点没有出口", n.getNodeName(),
						"连接后续节点或改为结束节点", true, d, ns, es));
		for (var entry : outgoing.entrySet())
			if (entry.getValue().stream().filter(e -> Boolean.TRUE.equals(e.getIsDefault())
					|| e.getConditionExpr() == null || e.getConditionExpr().isBlank()).count() > 1)
				result.add(finding(flowId, byKey.containsKey(entry.getKey()) ? byKey.get(entry.getKey()).getId() : null,
						"RULE", "HIGH", "MULTIPLE_DEFAULT_EDGES", "节点存在多个默认出口", entry.getKey(), "只保留一个默认出口", true, d,
						ns, es));
		for (var entry : outgoing.entrySet()) {
			if (entry.getValue().size() <= 1) {
				continue;
			}
			WorkflowNode source = byKey.get(entry.getKey());
			if (source != null && !"CONDITION".equals(upper(source.getNodeType()))) {
				result.add(finding(
						flowId,
						source.getId(),
						"RULE",
						"HIGH",
						"BRANCH_WITHOUT_GATEWAY",
						"普通节点存在多个出口",
						"普通节点的多个出口会被 Flowable 当作并行路径执行",
						"先连接条件节点，再从条件节点配置互斥分支",
						true,
						d,
						ns,
						es));
			}
		}
		if (hasCycle(ns, es))
			result.add(finding(flowId, null, "RULE", "HIGH", "FLOW_CYCLE", "流程存在循环通路", "当前引擎不允许审批流程形成循环",
					"移除回路，退回请使用节点退回规则", true, d, ns, es));
		if (d.getEntryNodeKey() != null && byKey.containsKey(d.getEntryNodeKey())) {
			Set<String> reachable = reachable(d.getEntryNodeKey(), es);
			for (WorkflowNode n : ns)
				if (!reachable.contains(n.getNodeKey()))
					result.add(finding(flowId, n.getId(), "RULE", "HIGH", "UNREACHABLE_NODE", "节点不可达", n.getNodeName(),
							"补充流程连线", true, d, ns, es));
			if (ns.stream().noneMatch(n -> reachable.contains(n.getNodeKey()) && "END".equals(upper(n.getNodeType()))))
				result.add(finding(flowId, null, "RULE", "HIGH", "NO_END_PATH", "流程没有可达的结束节点", "流程可能无法结束", "连接结束节点",
						true, d, ns, es));
		}
		if (includeAi) {
			WorkflowAiSetting setting = setting();
			if (!Boolean.TRUE.equals(setting.getEnabled()))
				throw new IllegalArgumentException("AI辅助功能未开启");
			if (!Boolean.TRUE.equals(d.getAiAssistEnabled()))
				throw new IllegalArgumentException("当前流程未开启AI辅助");
			WorkflowAiProvider provider = provider(setting);
			if (provider == null)
				result.add(finding(flowId, null, "AI", "MEDIUM", "AI_PROVIDER_UNAVAILABLE", "AI审查服务尚未接入",
						"确定性检查已完成，未向外部服务发送数据", "配置私有化AI Provider", false, d, ns, es));
			else
				for (var f : provider.review(d, ns, es))
					result.add(finding(flowId, f.nodeId(), "AI", f.severity(), f.category(), f.title(), f.description(),
							f.suggestion(), false, d, ns, es));
		}
		reviews.saveAll(result);
		audit.log(actor, "WORKFLOW_VALIDATE", "flowId=" + flowId + ", ai=" + includeAi + ", findings=" + result.size());
		return result;
	}

	@Transactional
	public WorkflowDefinition publish(String flowId, String actor) {
		WorkflowDefinition d = requireDefinition(flowId);
		List<WorkflowReview> findings = validate(flowId,
				Boolean.TRUE.equals(d.getAiAssistEnabled()) && Boolean.TRUE.equals(setting().getEnabled()), actor);
		if (findings.stream().anyMatch(x -> Boolean.TRUE.equals(x.getBlocking())))
			throw new IllegalArgumentException("流程存在阻断问题，不能发布");
		var deployed = flowableDeployment.deploy(d, nodes(flowId), edges(flowId));
		d.setFlowableDeploymentId(deployed.deploymentId());
		d.setFlowableProcessKey(deployed.processKey());
		d.setStatus("PUBLISHED");
		d.setPublishedAt(LocalDateTime.now());
		audit.log(actor, "WORKFLOW_PUBLISH",
				"flowId=" + flowId + ", version=" + d.getVersion() + ", deployment=" + deployed.deploymentId());
		return definitions.save(d);
	}

	public WorkflowAiSetting setting() {
		return settings.findAll().stream().findFirst().orElseGet(() -> {
			WorkflowAiSetting s = new WorkflowAiSetting();
			s.setEnabled(false);
			s.setAllowExternal(false);
			s.setMaskSensitiveData(true);
			s.setProviderMode("LOCAL_PRIVATE");
			return settings.save(s);
		});
	}

	@Transactional
	public WorkflowAiSetting updateSetting(WorkflowAiSetting input, String actor) {
		WorkflowAiSetting s = setting();
		s.setEnabled(Boolean.TRUE.equals(input.getEnabled()));
		s.setProviderMode(input.getProviderMode() == null ? "LOCAL_PRIVATE" : input.getProviderMode());
		s.setAllowExternal(Boolean.TRUE.equals(input.getAllowExternal()));
		s.setMaskSensitiveData(input.getMaskSensitiveData() == null || input.getMaskSensitiveData());
		audit.log(actor, "WORKFLOW_AI_SETTING_UPDATE",
				"enabled=" + s.getEnabled() + ", mode=" + s.getProviderMode() + ", external=" + s.getAllowExternal());
		return settings.save(s);
	}

	public Map<String, Object> aiDraft(String requirement) {
		WorkflowAiSetting s = setting();
		if (!Boolean.TRUE.equals(s.getEnabled()))
			throw new IllegalArgumentException("AI辅助功能未开启");
		WorkflowAiProvider p = provider(s);
		if (p == null)
			throw new IllegalArgumentException("未配置可用的私有化AI Provider");
		return p.draft(required(requirement, "流程需求不能为空"));
	}

	@Transactional
	public WorkflowInstance start(String flowId, String businessKey, String variables, Map<String, Object> formData,
			String actor) {
		WorkflowDefinition d = requireDefinition(flowId);
		if (!"PUBLISHED".equals(d.getStatus()))
			throw new IllegalArgumentException("只能发起已发布流程");
		if (!security.canStart(actor, flowId))
			throw new AccessDeniedException("不在该流程的发起范围内");
		Map<String, Object> submittedForm = formData == null ? Map.of() : formData;
		for (WorkflowStartValidator validator : startValidators) {
			if (validator.supports(d.getFlowCode())) {
				// 行业资源授权必须先于实例落库和附件绑定，失败时不产生半成品流程。
				validator.validate(actor, submittedForm);
			}
		}
		WorkflowInstance i = new WorkflowInstance();
		i.setDefinitionId(d.getId());
		i.setDefinitionVersion(d.getVersion());
		i.setBusinessKey(
				businessKey == null || businessKey.isBlank() ? d.getFlowCode() + "-" + System.currentTimeMillis()
						: businessKey.trim());
		i.setInitiator(actor);
		i.setVariablesJson(variables == null ? "{}" : variables);
		i.setCurrentNodeKey(d.getEntryNodeKey());
		// 从本版本开始，新流程实例只允许由 Flowable 推进。LEGACY 仅兼容历史存量实例。
		i.setEngineType("FLOWABLE");
		i = instances.save(i);
		addParticipant(i.getId(), actor, "INITIATOR", null);
		if (d.getMainFormId() != null && !d.getMainFormId().isBlank()) {
			List<FormField> fields = formService.fields(d.getMainFormId());
			Map<String, String> editable = new HashMap<>();
			for (FormField f : fields)
				editable.put(d.getMainFormId() + "." + f.getFieldKey(), "EDIT");
			formService.saveRuntime(i.getId(), d.getMainFormId(), "_MAIN", "MAIN", actor,
					submittedForm, editable, Set.of(), false);

			// 绑定操作加入流程发起事务，防止流程与附件引用出现半成功状态。
			List<String> attachmentIds = attachmentIds(submittedForm, fields);
			if (!attachmentIds.isEmpty()) {
				managedFiles.bind(attachmentIds, "WORKFLOW_FORM", i.getId(), actor);
			}
		}
		i = flowableRuntime.start(d, i, actor);
		audit.log(actor, "WORKFLOW_START", "flowId=" + flowId + ", instanceId=" + i.getId());
		return i;
	}

	private List<String> attachmentIds(
			Map<String, Object> formData,
			List<FormField> fields) {
		if (formData == null || formData.isEmpty()) {
			return List.of();
		}

		Set<String> ids = new LinkedHashSet<>();
		for (FormField field : fields) {
			if (!"FILE".equals(field.getFieldType())) {
				continue;
			}
			Object raw = formData.get(field.getFieldKey());
			if (!(raw instanceof List<?> attachments)) {
				continue;
			}
			for (Object attachment : attachments) {
				if (attachment instanceof Map<?, ?> metadata && metadata.get("id") != null) {
					ids.add(String.valueOf(metadata.get("id")));
				}
			}
		}
		return List.copyOf(ids);
	}

	@Transactional
	public Map<String, Object> runtimeForms(String instanceId, String actor) {
		WorkflowInstance instance = requireInstance(instanceId);
		if (flowableRuntime.isFlowable(instance)) {
			instance = flowableRuntime.synchronize(instance);
		}
		security.requireInstanceView(actor, instanceId);
		WorkflowDefinition definition = requireDefinition(instance.getDefinitionId());
		WorkflowNode node = nodes.findByFlowIdAndNodeKey(definition.getId(), instance.getCurrentNodeKey())
				.orElseThrow(() -> new IllegalArgumentException("当前节点不存在"));
		List<WorkflowTask> history = tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId);
		WorkflowTask currentTask = history.stream()
				.filter(t -> "PENDING".equals(t.getStatus()) && actor.equals(t.getAssignee()))
				.findFirst()
				.orElse(null);
		boolean starterRework = currentTask != null && "STARTER_REWORK".equals(currentTask.getTaskKind());
		Map<String, String> permissions = starterRework
				? starterReworkPermissions(definition, node)
				: permissionMap(node, "permissions");
		Set<String> required = permissionSet(node, "required");
		List<Map<String, Object>> forms = new ArrayList<>();
		if (definition.getMainFormId() != null && !definition.getMainFormId().isBlank())
			forms.add(runtimeForm(instance, definition.getMainFormId(), "_MAIN", "MAIN", permissions, required));
		if (!starterRework) {
			for (String id : additionalForms(node))
				forms.add(runtimeForm(instance, id, node.getNodeKey(), "ADDITIONAL", permissions, required));
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("instance", instanceView(instance, definition));
		result.put("flow", definitionView(definition));
		result.put("instanceId", instance.getId());
		result.put("nodeKey", node.getNodeKey());
		result.put("nodeName", node.getNodeName());
		result.put("forms", forms);
		result.put("history", history.stream().map(this::historyView).toList());
		result.put("currentTask", currentTask == null ? null : taskView(currentTask));
		result.put("canEdit", currentTask != null);
		result.put("operations", taskOperationView(node, currentTask));
		return result;
	}

	@Transactional
	public FormInstance saveRuntimeForm(String instanceId, String formId, Map<String, Object> data, boolean draft,
			String actor) {
		WorkflowInstance instance = requireInstance(instanceId);
		security.requireInstanceView(actor, instanceId);
		security.requireCurrentTask(actor, instanceId);
		WorkflowDefinition definition = requireDefinition(instance.getDefinitionId());
		WorkflowNode node = nodes.findByFlowIdAndNodeKey(definition.getId(), instance.getCurrentNodeKey())
				.orElseThrow(() -> new IllegalArgumentException("当前节点不存在"));
		WorkflowTask currentTask = currentPendingTask(instanceId, actor);
		boolean starterRework = "STARTER_REWORK".equals(currentTask.getTaskKind());
		boolean main = Objects.equals(definition.getMainFormId(), formId);
		if (starterRework && !main) {
			throw new IllegalArgumentException("发起人修改任务只能编辑主表单");
		}
		if (!starterRework && !main && !additionalForms(node).contains(formId))
			throw new IllegalArgumentException("当前节点未绑定该表单");
		Map<String, Object> runtimeData = data == null ? Map.of() : data;
		List<FormField> fields = formService.fields(formId);
		managedFiles.requireBound(
				attachmentIds(runtimeData, fields),
				"WORKFLOW_FORM",
				instanceId);
		Map<String, String> permissions = starterRework
				? starterReworkPermissions(definition, node)
				: permissionMap(node, "permissions");
		return formService.saveRuntime(instanceId, formId, main ? "_MAIN" : node.getNodeKey(),
				main ? "MAIN" : "ADDITIONAL", actor, runtimeData, permissions,
				permissionSet(node, "required"), draft);
	}

	@Transactional
	public List<Map<String, Object>> pending(String actor) {
		flowableRuntime.synchronizeAssignedTo(actor);
		Map<String, WorkflowTask> visible = new LinkedHashMap<>();
		for (WorkflowTask task : tasks.findByAssigneeAndStatusOrderByCreateTimeDesc(actor, "PENDING")) {
			visible.put(task.getId(), task);
		}
		List<String> candidateTaskIds = candidates.findBySubjectTypeAndSubjectId("USER", actor).stream()
				.map(WorkflowTaskCandidate::getTaskId)
				.toList();
		for (WorkflowTask task : tasks.findAllById(candidateTaskIds)) {
			if ("CLAIMABLE".equals(task.getStatus())) {
				visible.put(task.getId(), task);
			}
		}
		return visible.values().stream()
				.sorted(Comparator.comparing(WorkflowTask::getCreateTime,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(this::taskView)
				.toList();
	}

	@Transactional
	public Page<Map<String, Object>> pending(String actor, int page, int size) {
		return page(pending(actor), page, size);
	}

	/** 门户首页只返回少量可操作摘要，完整数据仍由流程任务中心分页承载。 */
	@Transactional
	public Map<String, Object> portalTodo(String actor) {
		List<Map<String, Object>> all = pending(actor);
		long overdue = all.stream()
				.filter(task -> Set.of("OVERDUE", "ESCALATED").contains(task.get("slaStatus")))
				.count();
		long claimable = all.stream()
				.filter(task -> Boolean.TRUE.equals(task.get("claimable")))
				.count();
		List<Map<String, Object>> items = all.stream()
				.limit(6)
				.map(task -> {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("taskId", task.get("id"));
					item.put("instanceId", task.get("instanceId"));
					item.put("flowName", task.get("flowName"));
					item.put("nodeName", task.get("nodeName"));
					item.put("businessKey", task.get("businessKey"));
					item.put("dueAt", task.get("dueAt"));
					item.put("slaStatus", task.get("slaStatus"));
					item.put("claimable", task.get("claimable"));
					item.put("route", "/portal/workflow-instances/" + task.get("instanceId") + "/forms");
					return item;
				})
				.toList();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("total", all.size());
		result.put("overdue", overdue);
		result.put("claimable", claimable);
		result.put("items", items);
		result.put("allRoute", "/portal/tasks");
		return result;
	}

	/** 门户“我的督办”指当前用户发起、仍在运行并允许其催办的流程。 */
	@Transactional(readOnly = true)
	public Map<String, Object> portalSupervision(String actor) {
		LocalDateTime now = LocalDateTime.now();
		List<WorkflowInstance> running = instances.findByInitiatorOrderByCreateTimeDesc(actor).stream()
				.filter(instance -> "RUNNING".equals(instance.getStatus()))
				.toList();
		long overdue = 0;
		List<Map<String, Object>> items = new ArrayList<>();
		for (WorkflowInstance instance : running) {
			List<WorkflowTask> activeTasks = tasks.findByInstanceIdOrderByCreateTimeAsc(instance.getId()).stream()
					.filter(task -> Set.of("PENDING", "CLAIMABLE").contains(task.getStatus()))
					.toList();
			boolean instanceOverdue = activeTasks.stream()
					.anyMatch(task -> task.getDueAt() != null && task.getDueAt().isBefore(now));
			if (instanceOverdue) {
				overdue++;
			}
			if (items.size() >= 6) {
				continue;
			}
			WorkflowDefinition definition = definitions.findById(instance.getDefinitionId()).orElse(null);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("instanceId", instance.getId());
			item.put("flowName", definition == null ? "历史流程" : definition.getFlowName());
			item.put("businessKey", instance.getBusinessKey());
			item.put("currentNodeKey", instance.getCurrentNodeKey());
			item.put("createTime", instance.getCreateTime());
			item.put("pendingCount", activeTasks.size());
			item.put("overdue", instanceOverdue);
			item.put("canRemind", !activeTasks.isEmpty());
			item.put("route", "/portal/workflow-instances/" + instance.getId() + "/forms");
			items.add(item);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("total", running.size());
		result.put("overdue", overdue);
		result.put("items", items);
		result.put("allRoute", "/portal/tasks?tab=initiated");
		return result;
	}

	@Transactional
	public WorkflowTask claimTask(String taskId, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId)
				.orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		WorkflowInstance instance = instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		if (!flowableRuntime.isFlowable(instance)) {
			throw new IllegalArgumentException("Legacy 任务不支持候选认领");
		}
		WorkflowTask result = flowableRuntime.claim(snapshot, actor);
		addParticipant(instance.getId(), actor, "ASSIGNEE", result.getId());
		audit.log(actor, "WORKFLOW_TASK_CLAIM", "taskId=" + taskId);
		return result;
	}

	@Transactional
	public WorkflowTask unclaimTask(String taskId, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId)
				.orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		WorkflowInstance instance = instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		if (!flowableRuntime.isFlowable(instance)) {
			throw new IllegalArgumentException("Legacy 任务不支持取消认领");
		}
		WorkflowTask result = flowableRuntime.unclaim(snapshot, actor);
		audit.log(actor, "WORKFLOW_TASK_UNCLAIM", "taskId=" + taskId);
		return result;
	}

	@Transactional(readOnly = true)
	public Map<String, List<WorkflowDelegation>> delegations(String actor) {
		return Map.of(
				"outgoing", delegations.findByDelegatorOrderByCreateTimeDesc(actor),
				"incoming", delegations.findByDelegateeOrderByCreateTimeDesc(actor));
	}

	@Transactional(readOnly = true)
	public Map<String, Page<WorkflowDelegation>> delegations(String actor, int page, int size) {
		Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		return Map.of(
				"outgoing", delegations.findByDelegatorOrderByCreateTimeDesc(actor, pageable),
				"incoming", delegations.findByDelegateeOrderByCreateTimeDesc(actor, pageable));
	}

	@Transactional
	public WorkflowDelegation saveDelegation(WorkflowDelegation value, String actor) {
		String delegatee = required(value.getDelegatee(), "受托人不能为空");
		if (actor.equals(delegatee)) {
			throw new IllegalArgumentException("不能委托给自己");
		}
		boolean userExists = assigneeResolver.directory().stream()
				.anyMatch(user -> delegatee.equals(user.get("username")));
		if (!userExists) {
			throw new IllegalArgumentException("受托人账号不存在");
		}
		if (value.getStartAt() == null || value.getEndAt() == null
				|| !value.getEndAt().isAfter(value.getStartAt())) {
			throw new IllegalArgumentException("委托结束时间必须晚于开始时间");
		}
		if (value.getDefinitionId() != null && !value.getDefinitionId().isBlank()) {
			requireDefinition(value.getDefinitionId());
		}
		boolean overlaps = delegations.findByDelegatorOrderByCreateTimeDesc(actor).stream()
				.filter(existing -> Boolean.TRUE.equals(existing.getEnabled()))
				.filter(existing -> Objects.equals(
						normalizeDefinition(existing.getDefinitionId()),
						normalizeDefinition(value.getDefinitionId())))
				.anyMatch(existing -> value.getStartAt().isBefore(existing.getEndAt())
						&& value.getEndAt().isAfter(existing.getStartAt()));
		if (overlaps) {
			throw new IllegalArgumentException("同一流程范围内存在时间重叠的委托规则");
		}
		value.setId(null);
		value.setDelegator(actor);
		value.setDelegatee(delegatee);
		value.setEnabled(true);
		WorkflowDelegation result = delegations.save(value);
		audit.log(actor, "WORKFLOW_DELEGATION_CREATE", "delegationId=" + result.getId()
				+ ", delegatee=" + delegatee);
		return result;
	}

	@Transactional
	public void deleteDelegation(String id, String actor) {
		WorkflowDelegation value = delegations.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("委托规则不存在"));
		if (!actor.equals(value.getDelegator())) {
			throw new AccessDeniedException("只能删除自己创建的委托规则");
		}
		delegations.delete(value);
		audit.log(actor, "WORKFLOW_DELEGATION_DELETE", "delegationId=" + id);
	}

	private String normalizeDefinition(String definitionId) {
		return definitionId == null || definitionId.isBlank() ? "*" : definitionId.trim();
	}

	@Transactional
	public WorkflowInstance completeTask(String taskId, boolean approved, String comment, String actor) {
		if (!approved)
			throw new IllegalArgumentException("拒绝任务请使用独立拒绝接口");
		WorkflowTask snapshot = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		// 锁定实例后重新读取任务。多人同时审批时，后到事务会看到前一事务的最新状态，节点只会推进一次。
		WorkflowInstance i = instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		WorkflowTask task = requirePendingTask(taskId, actor);
		WorkflowNode node = nodes.findByFlowIdAndNodeKey(i.getDefinitionId(), task.getNodeKey()).orElseThrow();
		requireOperation(node, "approve");
		validateCurrentNodeRequiredForms(i, node);
		if (flowableRuntime.isFlowable(i)) {
			WorkflowInstance result = flowableRuntime.approve(i, task, actor, comment);
			audit.log(actor, "WORKFLOW_TASK_APPROVE", "taskId=" + taskId + ", engine=FLOWABLE");
			publishCompletion(result, actor);
			return result;
		}
		task.setStatus("APPROVED");
		task.setComment(comment);
		task.setCompletedAt(LocalDateTime.now());
		tasks.saveAndFlush(task);
		if ("STARTER_REWORK".equals(task.getTaskKind()))
			throw new IllegalArgumentException("发起人修改任务请使用重新提交操作");
		String mode = approvalMode(node);
		List<WorkflowTask> round = approvalRound(i.getId(), task);
		if ("SEQUENTIAL".equals(mode)) {
			WorkflowTask next = round.stream().filter(t -> "WAITING".equals(t.getStatus())).findFirst().orElse(null);
			if (next != null) {
				next.setStatus("PENDING");
				tasks.save(next);
			} else
				advance(i, task.getNodeKey(), actor);
		} else {
			long approvedCount = round.stream().filter(t -> "APPROVED".equals(t.getStatus())).count();
			int required = requiredApprovals(node, round.size());
			if (approvedCount >= required) {
				cancelRoundPending(round, "审批条件已满足");
				advance(i, task.getNodeKey(), actor);
			}
		}
		audit.log(actor, "WORKFLOW_TASK_APPROVE", "taskId=" + taskId + ", mode=" + mode);
		WorkflowInstance result = instances.save(i);
		publishCompletion(result, actor);
		return result;
	}

	/** 快捷审批和 API 直调同样必须满足当前节点主表单、附加表单的必填约束。 */
	private void validateCurrentNodeRequiredForms(WorkflowInstance instance, WorkflowNode node) {
		WorkflowDefinition definition = requireDefinition(instance.getDefinitionId());
		Map<String, String> permissions = permissionMap(node, "permissions");
		Set<String> required = permissionSet(node, "required");
		if (definition.getMainFormId() != null && !definition.getMainFormId().isBlank()) {
			formService.validateRuntimeRequiredFields(
					instance.getId(),
					definition.getMainFormId(),
					"_MAIN",
					permissions,
					required);
		}
		for (String formId : additionalForms(node)) {
			formService.validateRuntimeRequiredFields(
					instance.getId(),
					formId,
					node.getNodeKey(),
					permissions,
					required);
		}
	}

	/**
	 * 仅在实例真正完成时发布一次业务事件。监听器在事务提交后执行，避免审批事务回滚时提前回写业务表。
	 */
	private void publishCompletion(WorkflowInstance instance, String actor) {
		if (!"COMPLETED".equals(instance.getStatus())) {
			return;
		}
		WorkflowDefinition definition = requireDefinition(instance.getDefinitionId());
		Map<String, Object> mainFormData = formService
				.instance(instance.getId(), definition.getMainFormId(), "_MAIN")
				.map(FormInstance::getDataJson)
				.map(this::readMap)
				.orElseGet(Map::of);
		eventPublisher.publishEvent(new WorkflowCompletedEvent(
				instance.getId(),
				definition.getId(),
				definition.getFlowCode(),
				instance.getBusinessKey(),
				instance.getInitiator(),
				actor,
				Map.copyOf(mainFormData)));
	}

	@Transactional
	public WorkflowInstance rejectTask(String taskId, String targetNodeKey, String comment, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		WorkflowInstance instance = instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		WorkflowTask task = requirePendingTask(taskId, actor);
		requireOperation(task, "reject");
		String policy = nodeProperty(task, "rejectPolicy", "TERMINATE");
		if (flowableRuntime.isFlowable(instance)) {
			WorkflowInstance result;
			if ("TERMINATE".equals(policy)) {
				result = flowableRuntime.rejectAndTerminate(instance, task, comment);
				cancelPending(instance.getId(), operationComment("审批拒绝", comment));
				result.setStatus("REJECTED");
				result.setFinishedAt(LocalDateTime.now());
			} else if ("STARTER".equals(policy)) {
				result = flowableRuntime.moveToStarter(instance, task, comment);
			} else {
				String target = resolveReturnTarget(instance, task, policy, targetNodeKey);
				result = flowableRuntime.moveTo(instance, task, target, comment);
			}
			audit.log(actor, "WORKFLOW_TASK_REJECT", "taskId=" + taskId + ", policy=" + policy + ", engine=FLOWABLE");
			return instances.save(result);
		}
		task.setStatus("REJECTED");
		task.setComment(comment);
		task.setCompletedAt(LocalDateTime.now());
		tasks.saveAndFlush(task);
		if ("TERMINATE".equals(policy)) {
			cancelPending(instance.getId(), operationComment("审批拒绝", comment));
			instance.setStatus("REJECTED");
			instance.setFinishedAt(LocalDateTime.now());
		} else
			moveBack(instance, task, policy, targetNodeKey, comment, actor);
		audit.log(actor, "WORKFLOW_TASK_REJECT", "taskId=" + taskId + ", policy=" + policy);
		return instances.save(instance);
	}

	@Transactional
	public WorkflowInstance resubmitTask(String taskId, String comment, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		WorkflowInstance instance = instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		WorkflowTask task = requirePendingTask(taskId, actor);
		if (!"STARTER_REWORK".equals(task.getTaskKind()))
			throw new IllegalArgumentException("当前任务不是发起人修改任务");
		WorkflowNode resume = nodes.findByFlowIdAndNodeKey(instance.getDefinitionId(), task.getResumeNodeKey())
				.orElseThrow(() -> new IllegalArgumentException("原审批节点不存在"));
		validateStarterReworkForm(instance, resume);
		if (flowableRuntime.isFlowable(instance)) {
			WorkflowInstance result = flowableRuntime.resubmit(instance, task, actor, comment);
			audit.log(actor, "WORKFLOW_STARTER_RESUBMIT", "taskId=" + taskId
					+ ", resume=" + resume.getNodeKey() + ", engine=FLOWABLE");
			return result;
		}
		task.setStatus("APPROVED");
		task.setComment(operationComment("发起人重新提交", comment));
		task.setCompletedAt(LocalDateTime.now());
		tasks.saveAndFlush(task);
		instance.setCurrentNodeKey(resume.getNodeKey());
		instances.save(instance);
		createTask(instance, resume, actor);
		audit.log(actor, "WORKFLOW_STARTER_RESUBMIT", "taskId=" + taskId + ", resume=" + resume.getNodeKey());
		return instance;
	}

	private void validateStarterReworkForm(WorkflowInstance instance, WorkflowNode resumeNode) {
		WorkflowDefinition definition = requireDefinition(instance.getDefinitionId());
		if (definition.getMainFormId() == null || definition.getMainFormId().isBlank()) {
			return;
		}
		formService.validateRuntimeRequiredFields(
				instance.getId(),
				definition.getMainFormId(),
				"_MAIN",
				starterReworkPermissions(definition, resumeNode),
				permissionSet(resumeNode, "required"));
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> handled(String actor) {
		return tasks.findByAssigneeOrderByCreateTimeDesc(actor).stream().filter(t -> !"PENDING".equals(t.getStatus()))
				.map(this::taskView).toList();
	}

	@Transactional(readOnly = true)
	public Page<Map<String, Object>> handled(String actor, int page, int size) {
		return page(handled(actor), page, size);
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> initiated(String actor) {
		return instances.findByInitiatorOrderByCreateTimeDesc(actor).stream()
				.map(i -> instanceView(i, requireDefinition(i.getDefinitionId()))).toList();
	}

	@Transactional(readOnly = true)
	public Page<Map<String, Object>> initiated(String actor, int page, int size) {
		return page(initiated(actor), page, size);
	}

	public List<Map<String, String>> directoryUsers(String actor) {
		return assigneeResolver.directory().stream()
				.filter(u -> security.canViewDirectoryUser(actor, u.get("username"))).toList();
	}

	/**
	 * 人工选择的任务目标必须来自操作者有权查看的有效账号目录。前端下拉不是安全边界，
	 * 服务端再次校验可以阻止伪造用户名、停用账号以及跨数据范围转办。
	 */
	private String requireDirectoryTarget(String actor, String username, String emptyMessage) {
		String target = required(username, emptyMessage);
		boolean visibleActiveUser = assigneeResolver.directory().stream()
				.anyMatch(user -> target.equals(user.get("username"))
						&& security.canViewDirectoryUser(actor, target));
		if (!visibleActiveUser) {
			throw new AccessDeniedException("目标用户不存在、已停用或不在可选范围内");
		}
		return target;
	}

	public Map<String, Object> monitor(String actor) {
		List<WorkflowInstance> all = instances.findAll().stream()
				.filter(i -> security.canViewInstance(actor, i.getId())).toList();
		Set<String> ids = all.stream().map(WorkflowInstance::getId).collect(java.util.stream.Collectors.toSet());
		List<WorkflowTask> allTasks = tasks.findAll().stream().filter(t -> ids.contains(t.getInstanceId())).toList();
		long openIncidents = incidents.findByStatusOrderByCreateTimeDesc("OPEN").stream()
				.filter(incident -> ids.contains(incident.getInstanceId()))
				.count();
		LocalDateTime now = LocalDateTime.now();
		return Map.of("instances", all.size(), "running",
				all.stream().filter(i -> "RUNNING".equals(i.getStatus())).count(), "completed",
				all.stream().filter(i -> "COMPLETED".equals(i.getStatus())).count(), "rejected",
				all.stream().filter(i -> "REJECTED".equals(i.getStatus())).count(), "pendingTasks",
				allTasks.stream().filter(t -> Set.of("PENDING", "CLAIMABLE").contains(t.getStatus())).count(), "overdueTasks",
				allTasks.stream().filter(
						t -> Set.of("PENDING", "CLAIMABLE").contains(t.getStatus())
								&& t.getDueAt() != null && t.getDueAt().isBefore(now))
						.count(),
				"openIncidents", openIncidents);
	}

	private <T> Page<T> page(List<T> values, int page, int size) {
		Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		int from = Math.min((int) pageable.getOffset(), values.size());
		int to = Math.min(from + pageable.getPageSize(), values.size());
		return new PageImpl<>(values.subList(from, to), pageable, values.size());
	}

	@Transactional
	public WorkflowTask remind(String taskId, String actor) {
		WorkflowTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		if (!security.canRemind(actor, taskId))
			throw new AccessDeniedException("仅发起人或流程管理员可以催办");
		if (!Set.of("PENDING", "CLAIMABLE").contains(task.getStatus()))
			throw new IllegalArgumentException("仅待办任务可以催办");
		return sla.manualRemind(task, actor);
	}

	@Transactional
	public int remindCurrentTasks(String instanceId, String actor) {
		WorkflowInstance instance = requireInstance(instanceId);
		if (!actor.equals(instance.getInitiator()) && !security.canManageInstance(actor, instanceId)) {
			throw new AccessDeniedException("仅发起人或流程管理员可以催办");
		}
		List<WorkflowTask> current = tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId).stream()
				.filter(task -> Set.of("PENDING", "CLAIMABLE").contains(task.getStatus()))
				.toList();
		if (current.isEmpty()) {
			throw new IllegalArgumentException("当前流程没有可催办任务");
		}
		current.forEach(task -> sla.manualRemind(task, actor));
		return current.size();
	}

	@Transactional
	public void markOverdueTasks() {
		sla.scan();
	}

	@Transactional
	public WorkflowTask transferTask(String taskId, String assignee, String comment, String actor) {
		WorkflowTask task = requirePendingTask(taskId, actor);
		requireOperation(task, "transfer");
		String target = requireDirectoryTarget(actor, assignee, "转办人不能为空");
		task.setComment(operationComment("转办给 " + target, comment));
		WorkflowInstance instance = requireInstance(task.getInstanceId());
		if (flowableRuntime.isFlowable(instance)) {
			task = flowableRuntime.transfer(task, actor, target, task.getComment());
			addParticipant(task.getInstanceId(), target, "ASSIGNEE", task.getId());
			addCandidate(task.getId(), "USER", target);
			audit.log(actor, "WORKFLOW_TASK_TRANSFER", "taskId=" + taskId + ", target=" + target);
			return task;
		}
		task.setAssignee(target);
		addParticipant(task.getInstanceId(), target, "ASSIGNEE", task.getId());
		addCandidate(task.getId(), "USER", target);
		audit.log(actor, "WORKFLOW_TASK_TRANSFER", "taskId=" + taskId + ", target=" + target);
		return tasks.save(task);
	}

	@Transactional
	public WorkflowTask addSign(String taskId, String assignee, String comment, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		WorkflowTask source = requirePendingTask(taskId, actor);
		requireOperation(source, "addSign");
		WorkflowInstance instance = requireInstance(source.getInstanceId());
		String target = requireDirectoryTarget(actor, assignee, "加签人不能为空");
		if (flowableRuntime.isFlowable(instance)) {
			WorkflowTask task = flowableRuntime.addSign(
					instance,
					source,
					actor,
					target,
					operationComment("由 " + actor + " 加签", comment));
			addParticipant(task.getInstanceId(), target, "ASSIGNEE", task.getId());
			addCandidate(task.getId(), "USER", target);
			audit.log(actor, "WORKFLOW_TASK_ADD_SIGN", "taskId=" + taskId + ", assignee=" + target + ", engine=FLOWABLE");
			return task;
		}
		WorkflowTask task = new WorkflowTask();
		task.setInstanceId(source.getInstanceId());
		task.setNodeKey(source.getNodeKey());
		task.setNodeName(source.getNodeName());
		task.setRoundKey(source.getRoundKey());
		WorkflowNode node = nodes.findByFlowIdAndNodeKey(instance.getDefinitionId(), source.getNodeKey()).orElseThrow();
		if ("SEQUENTIAL".equals(approvalMode(node)))
			task.setStatus("WAITING");
		task.setAssignee(target);
		task.setComment(operationComment("由 " + actor + " 加签", comment));
		task = tasks.save(task);
		addParticipant(task.getInstanceId(), task.getAssignee(), "ASSIGNEE", task.getId());
		addCandidate(task.getId(), "USER", task.getAssignee());
		audit.log(actor, "WORKFLOW_TASK_ADD_SIGN", "taskId=" + taskId + ", assignee=" + assignee);
		return task;
	}

	@Transactional
	public WorkflowTask ccTask(String taskId, String assignee, String comment, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		WorkflowTask source = requirePendingTask(taskId, actor);
		requireOperation(source, "cc");
		WorkflowTask task = new WorkflowTask();
		task.setInstanceId(source.getInstanceId());
		task.setNodeKey(source.getNodeKey());
		task.setNodeName(source.getNodeName() + "（抄送）");
		task.setAssignee(requireDirectoryTarget(actor, assignee, "抄送人不能为空"));
		task.setStatus("CC");
		task.setComment(operationComment("由 " + actor + " 抄送", comment));
		task.setCompletedAt(LocalDateTime.now());
		task = tasks.save(task);
		addParticipant(task.getInstanceId(), task.getAssignee(), "CC", task.getId());
		audit.log(actor, "WORKFLOW_TASK_CC", "taskId=" + taskId + ", assignee=" + assignee);
		return task;
	}

	@Transactional
	public WorkflowInstance returnTask(String taskId, String targetNodeKey, String comment, String actor) {
		WorkflowTask snapshot = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		WorkflowInstance instance = instances.findLockedById(snapshot.getInstanceId()).orElseThrow();
		WorkflowTask task = requirePendingTask(taskId, actor);
		requireOperation(task, "return");
		String policy = nodeProperty(task, "returnPolicy", "PREVIOUS");
		if (flowableRuntime.isFlowable(instance)) {
			WorkflowInstance result;
			if ("STARTER".equals(policy)) {
				result = flowableRuntime.moveToStarter(instance, task, comment);
			} else {
				String target = resolveReturnTarget(instance, task, policy, targetNodeKey);
				result = flowableRuntime.moveTo(instance, task, target, comment);
			}
			audit.log(actor, "WORKFLOW_TASK_RETURN", "taskId=" + taskId + ", policy=" + policy + ", engine=FLOWABLE");
			return result;
		}
		moveBack(instance, task, policy, targetNodeKey, comment, actor);
		audit.log(actor, "WORKFLOW_TASK_RETURN", "taskId=" + taskId + ", policy=" + policy);
		return instances.save(instance);
	}

	@Transactional
	public WorkflowInstance withdraw(String instanceId, String comment, String actor) {
		WorkflowInstance instance = instances.findLockedById(instanceId)
				.orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
		if (!actor.equals(instance.getInitiator()))
			throw new AccessDeniedException("仅发起人可撤回");
		if (!"RUNNING".equals(instance.getStatus()))
			throw new IllegalArgumentException("当前状态不能撤回");
		if (flowableRuntime.isFlowable(instance)) {
			flowableRuntime.terminate(instance, operationComment("发起人撤回", comment));
		}
		cancelPending(instanceId, operationComment("发起人撤回", comment));
		instance.setStatus("WITHDRAWN");
		instance.setFinishedAt(LocalDateTime.now());
		audit.log(actor, "WORKFLOW_INSTANCE_WITHDRAW", "instanceId=" + instanceId);
		return instances.save(instance);
	}

	@Transactional
	public WorkflowInstance terminate(String instanceId, String reason, String actor) {
		WorkflowInstance instance = instances.findLockedById(instanceId)
				.orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
		if (!security.canManageInstance(actor, instanceId))
			throw new AccessDeniedException("无权终止该流程实例");
		if (!"RUNNING".equals(instance.getStatus()))
			throw new IllegalArgumentException("仅运行中的流程可以终止");
		if (flowableRuntime.isFlowable(instance)) {
			flowableRuntime.terminate(instance, operationComment("管理员终止", reason));
		}
		cancelPending(instanceId, operationComment("管理员终止", reason));
		instance.setStatus("TERMINATED");
		instance.setFinishedAt(LocalDateTime.now());
		audit.log(actor, "WORKFLOW_INSTANCE_TERMINATE",
				"instanceId=" + instanceId + ", reason=" + (reason == null ? "" : reason));
		return instances.save(instance);
	}

	private WorkflowTask requirePendingTask(String id, String actor) {
		WorkflowTask task = tasks.findById(id).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
		if (!"PENDING".equals(task.getStatus()))
			throw new IllegalStateException("任务已处理，请勿重复提交");
		if (!actor.equals(task.getAssignee()))
			throw new AccessDeniedException("不是当前任务处理人");
		return task;
	}

	private void cancelPending(String instanceId, String comment) {
		for (WorkflowTask task : tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId))
			if (Set.of("PENDING", "CLAIMABLE", "WAITING").contains(task.getStatus())) {
				task.setStatus("CANCELLED");
				task.setComment(comment);
				task.setCompletedAt(LocalDateTime.now());
				tasks.save(task);
			}
	}

	private String operationComment(String action, String comment) {
		return comment == null || comment.isBlank() ? action : action + "：" + comment.trim();
	}

	/**
	 * 执行节点退回。策略来自节点配置而不是请求参数，防止调用方绕过流程设计器约束。 SELECTABLE
	 * 只能选择已经实际审批通过的历史节点，不能任意跳转到未经过的节点。
	 */
	private void moveBack(WorkflowInstance instance, WorkflowTask source, String policy, String requestedTarget,
			String comment, String actor) {
		String normalized = upper(policy);
		cancelPending(instance.getId(), operationComment("退回", comment));
		if ("STARTER".equals(normalized)) {
			WorkflowTask rework = new WorkflowTask();
			rework.setInstanceId(instance.getId());
			rework.setNodeKey(source.getNodeKey());
			rework.setNodeName("发起人修改后重新提交");
			rework.setAssignee(instance.getInitiator());
			rework.setTaskKind("STARTER_REWORK");
			rework.setResumeNodeKey(source.getNodeKey());
			rework.setRoundKey(UUID.randomUUID().toString());
			rework = tasks.save(rework);
			addParticipant(instance.getId(), instance.getInitiator(), "ASSIGNEE", rework.getId());
			addCandidate(rework.getId(), "USER", instance.getInitiator());
			instance.setCurrentNodeKey(source.getNodeKey());
			instances.save(instance);
			return;
		}
		List<String> historyTargets = returnTargetKeys(instance.getId(), source.getNodeKey());
		String targetKey;
		if ("SELECTABLE".equals(normalized)) {
			targetKey = required(requestedTarget, "请选择退回节点");
			if (!historyTargets.contains(targetKey))
				throw new IllegalArgumentException("只能退回到已经审批通过的历史节点");
		} else {
			if (historyTargets.isEmpty())
				throw new IllegalArgumentException("没有可退回的上一人工节点");
			targetKey = historyTargets.get(historyTargets.size() - 1);
		}
		WorkflowNode target = nodes.findByFlowIdAndNodeKey(instance.getDefinitionId(), targetKey)
				.orElseThrow(() -> new IllegalArgumentException("退回节点不存在"));
		instance.setCurrentNodeKey(target.getNodeKey());
		instances.save(instance);
		createTask(instance, target, actor);
	}

	private List<String> returnTargetKeys(String instanceId, String currentNodeKey) {
		LinkedHashSet<String> values = new LinkedHashSet<>();
		tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId).stream().filter(t -> "APPROVED".equals(t.getStatus())
				&& !currentNodeKey.equals(t.getNodeKey()) && !"STARTER_REWORK".equals(t.getTaskKind()))
				.forEach(t -> values.add(t.getNodeKey()));
		return new ArrayList<>(values);
	}

	private String resolveReturnTarget(WorkflowInstance instance, WorkflowTask source, String policy,
			String requestedTarget) {
		String normalized = upper(policy);
		if ("STARTER".equals(normalized)) {
			throw new IllegalArgumentException("当前 Flowable BPMN 未配置发起人修改节点，不能退回发起人");
		}
		List<String> historyTargets = returnTargetKeys(instance.getId(), source.getNodeKey());
		if ("SELECTABLE".equals(normalized)) {
			String target = required(requestedTarget, "请选择退回节点");
			if (!historyTargets.contains(target)) {
				throw new IllegalArgumentException("只能退回到已经审批通过的历史节点");
			}
			return target;
		}
		if (historyTargets.isEmpty()) {
			throw new IllegalArgumentException("没有可退回的上一人工节点");
		}
		return historyTargets.getLast();
	}

	private List<WorkflowTask> approvalRound(String instanceId, WorkflowTask current) {
		return tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId).stream()
				.filter(t -> Objects.equals(t.getNodeKey(), current.getNodeKey()))
				.filter(t -> current.getRoundKey() == null ? Objects.equals(t.getId(), current.getId())
						: Objects.equals(t.getRoundKey(), current.getRoundKey()))
				.filter(t -> !"CC".equals(t.getStatus())).toList();
	}

	private int requiredApprovals(WorkflowNode node, int total) {
		String mode = approvalMode(node);
		if ("ANY".equals(mode))
			return 1;
		if ("ALL".equals(mode))
			return total;
		if ("COUNT".equals(mode))
			return Math.min(total, Math.max(1, nodeIntProperty(node, "approvalCount", 1)));
		if ("PERCENTAGE".equals(mode)) {
			int percentage = Math.min(100, Math.max(1, nodeIntProperty(node, "approvalPercentage", 100)));
			return Math.max(1, (int) Math.ceil(total * percentage / 100.0));
		}
		return 1;
	}

	private void cancelRoundPending(List<WorkflowTask> round, String reason) {
		for (WorkflowTask sibling : round)
			if (Set.of("PENDING", "WAITING").contains(sibling.getStatus())) {
				sibling.setStatus("CANCELLED");
				sibling.setComment(reason);
				sibling.setCompletedAt(LocalDateTime.now());
				tasks.save(sibling);
			}
	}

	private void advance(WorkflowInstance i, String from, String actor) {
		WorkflowDefinition d = requireDefinition(i.getDefinitionId());
		WorkflowNode current = nodes.findByFlowIdAndNodeKey(d.getId(), from)
				.orElseThrow(() -> new IllegalArgumentException("当前节点不存在"));
		if ("END".equals(upper(current.getNodeType()))) {
			i.setStatus("COMPLETED");
			i.setFinishedAt(LocalDateTime.now());
			instances.save(i);
			return;
		}
		List<WorkflowEdge> outgoing = edges.findByFlowIdAndFromNodeKey(d.getId(), from);
		WorkflowEdge next = choose(outgoing, i.getVariablesJson());
		if (next == null) {
			if ("START".equals(upper(current.getNodeType())))
				throw new IllegalArgumentException("入口节点没有有效出口");
			i.setCurrentNodeKey(from);
			createTask(i, current, actor);
			return;
		}
		WorkflowNode target = nodes.findByFlowIdAndNodeKey(d.getId(), next.getToNodeKey()).orElseThrow();
		i.setCurrentNodeKey(target.getNodeKey());
		instances.save(i);
		String type = upper(target.getNodeType());
		if ("END".equals(type)) {
			i.setStatus("COMPLETED");
			i.setFinishedAt(LocalDateTime.now());
			instances.save(i);
		} else if (Set.of("START", "CONDITION", "CC").contains(type))
			advance(i, target.getNodeKey(), actor);
		else if (Set.of("SERVICE_TASK", "HTTP_TASK", "AGENT_TASK", "MESSAGE_TASK").contains(type)) {
			i.setVariablesJson(executorRegistry.execute(target, i.getVariablesJson()));
			instances.save(i);
			audit.log(actor, "WORKFLOW_AUTO_NODE",
					"instanceId=" + i.getId() + ", node=" + target.getNodeKey() + ", executor=" + target.getExecutor());
			advance(i, target.getNodeKey(), actor);
		} else
			createTask(i, target, actor);
	}

	private void createTask(WorkflowInstance i, WorkflowNode n, String actor) {
		List<String> assignees = assigneeResolver.resolve(n, i, actor);
		if (assignees.isEmpty())
			throw new IllegalArgumentException("节点未解析到有效处理人：" + n.getNodeName());
		String mode = approvalMode(n);
		if ("SINGLE".equals(mode))
			assignees = assignees.subList(0, 1);
		int dueHours = dueHours(n);
		String roundKey = UUID.randomUUID().toString();
		for (int index = 0; index < assignees.size(); index++) {
			String assignee = assignees.get(index);
			WorkflowTask t = new WorkflowTask();
			t.setInstanceId(i.getId());
			t.setNodeKey(n.getNodeKey());
			t.setNodeName(n.getNodeName());
			t.setAssignee(assignee);
			t.setRoundKey(roundKey);
			if ("SEQUENTIAL".equals(mode) && index > 0)
				t.setStatus("WAITING");
			if (dueHours > 0)
				t.setDueAt(LocalDateTime.now().plusHours(dueHours));
			t = tasks.save(t);
			addParticipant(i.getId(), assignee, "ASSIGNEE", t.getId());
			addCandidate(t.getId(), "USER", assignee);
		}
	}

	private String resolveAssignee(WorkflowNode n, String actor) {
		try {
			JsonNode p = json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson());
			String v = p.path("assigneeValue").asText(p.path("assignee").asText());
			return "USER".equals(p.path("assigneeMode").asText("USER")) && !v.isBlank() ? v : actor;
		} catch (Exception e) {
			return actor;
		}
	}

	private String approvalMode(WorkflowNode n) {
		try {
			return json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson()).path("approvalMode")
					.asText("SINGLE");
		} catch (Exception e) {
			return "SINGLE";
		}
	}

	private String nodeStringProperty(WorkflowNode n, String key, String fallback) {
		try {
			return upper(json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson()).path(key)
					.asText(fallback));
		} catch (Exception e) {
			return fallback;
		}
	}

	private int nodeIntProperty(WorkflowNode n, String key, int fallback) {
		try {
			return json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson()).path(key)
					.asInt(fallback);
		} catch (Exception e) {
			return fallback;
		}
	}

	private String nodeProperty(WorkflowTask task, String key, String fallback) {
		WorkflowInstance i = requireInstance(task.getInstanceId());
		WorkflowNode n = nodes.findByFlowIdAndNodeKey(i.getDefinitionId(), task.getNodeKey())
				.orElseThrow(() -> new IllegalArgumentException("任务节点不存在"));
		try {
			return upper(json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson()).path(key)
					.asText(fallback));
		} catch (Exception e) {
			return fallback;
		}
	}

	private int dueHours(WorkflowNode n) {
		try {
			return Math.max(0, json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson())
					.path("dueHours").asInt(0));
		} catch (Exception e) {
			return 0;
		}
	}

	private boolean operationEnabled(WorkflowNode n, String operation) {
		try {
			JsonNode operations = json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson())
					.path("operations");
			return !operations.has(operation) || operations.path(operation).asBoolean(true);
		} catch (Exception e) {
			return true;
		}
	}

	private void requireOperation(WorkflowNode n, String operation) {
		if (!operationEnabled(n, operation))
			throw new AccessDeniedException("当前节点不允许执行该操作：" + operation);
	}

	private void requireOperation(WorkflowTask task, String operation) {
		WorkflowInstance i = requireInstance(task.getInstanceId());
		WorkflowNode n = nodes.findByFlowIdAndNodeKey(i.getDefinitionId(), task.getNodeKey())
				.orElseThrow(() -> new IllegalArgumentException("任务节点不存在"));
		requireOperation(n, operation);
	}

	private Map<String, Boolean> operationView(WorkflowNode n, boolean current) {
		Map<String, Boolean> value = new LinkedHashMap<>();
		for (String op : List.of("approve", "reject", "return", "transfer", "addSign", "cc")) {
			value.put(op, current && operationEnabled(n, op));
		}
		// Flowable 动态加签依赖多实例节点。单人审批节点展示该操作只会让用户
		// 在提交后收到错误，因此接口视图也明确关闭，前后端保持同一规则。
		if ("SINGLE".equals(approvalMode(n))) {
			value.put("addSign", false);
		}
		return value;
	}

	/**
	 * 发起人修改任务不是审批任务，只允许重新提交。这里从接口层明确关闭审批类操作，
	 * 避免门户仅靠 taskKind 猜测能力，也防止后续其他客户端错误展示审批按钮。
	 */
	Map<String, Boolean> taskOperationView(WorkflowNode node, WorkflowTask task) {
		if (task == null) {
			return operationView(node, false);
		}
		if (!"STARTER_REWORK".equals(task.getTaskKind())) {
			return operationView(node, true);
		}

		Map<String, Boolean> value = operationView(node, false);
		value.put("resubmit", true);
		return value;
	}

	private void addParticipant(String instanceId, String username, String type, String taskId) {
		if (username == null || username.isBlank() || participants
				.existsByInstanceIdAndUsernameAndParticipantTypeAndActiveTrue(instanceId, username, type))
			return;
		WorkflowInstanceParticipant p = new WorkflowInstanceParticipant();
		p.setInstanceId(instanceId);
		p.setUsername(username);
		p.setParticipantType(type);
		p.setSourceTaskId(taskId);
		participants.save(p);
	}

	private void addCandidate(String taskId, String type, String id) {
		WorkflowTaskCandidate c = new WorkflowTaskCandidate();
		c.setTaskId(taskId);
		c.setSubjectType(type);
		c.setSubjectId(id);
		try {
			candidates.save(c);
		} catch (org.springframework.dao.DataIntegrityViolationException ignored) {
		}
	}

	private WorkflowDefinitionAcl addAcl(String definitionId, String type, String id, String action) {
		WorkflowDefinitionAcl a = new WorkflowDefinitionAcl();
		a.setDefinitionId(definitionId);
		a.setSubjectType(upper(type));
		a.setSubjectId(required(id, "ACL主体不能为空"));
		a.setAction(upper(action));
		a.setEnabled(true);
		return acls.save(a);
	}

	private WorkflowEdge choose(List<WorkflowEdge> values, String variables) {
		for (WorkflowEdge e : values)
			if (e.getConditionExpr() != null && !e.getConditionExpr().isBlank()
					&& matches(e.getConditionExpr(), variables))
				return e;
		return values.stream().filter(e -> Boolean.TRUE.equals(e.getIsDefault()) || e.getConditionExpr() == null
				|| e.getConditionExpr().isBlank()).findFirst().orElse(null);
	}

	private boolean matches(String expr, String variables) {
		try {
			Matcher m = CONDITION.matcher(expr.trim());
			if (!m.matches())
				return false;
			JsonNode v = json.readTree(variables == null ? "{}" : variables).path(m.group(1));
			String right = m.group(3).trim().replaceAll("^[\\\"']|[\\\"']$", "");
			int c = v.isNumber() ? Double.compare(v.asDouble(), Double.parseDouble(right))
					: v.asText().compareTo(right);
			return switch (m.group(2)) {
			case "==" -> c == 0;
			case "!=" -> c != 0;
			case ">" -> c > 0;
			case "<" -> c < 0;
			case ">=" -> c >= 0;
			case "<=" -> c <= 0;
			default -> false;
			};
		} catch (Exception e) {
			return false;
		}
	}

	private WorkflowAiProvider provider(WorkflowAiSetting s) {
		return aiProviders.stream().filter(p -> p.mode().equalsIgnoreCase(s.getProviderMode()))
				.filter(p -> !p.external() || Boolean.TRUE.equals(s.getAllowExternal())).findFirst().orElse(null);
	}

	private boolean hasAssignee(WorkflowNode n) {
		try {
			JsonNode p = json.readTree(n.getPropertiesJson() == null ? "{}" : n.getPropertiesJson());
			if ("INITIATOR_MANAGER".equals(p.path("assigneeMode").asText()))
				return true;
			return !(p.path("assigneeValue").asText().isBlank() && p.path("assignee").asText().isBlank()
					&& p.path("assigneeRoleCode").asText().isBlank() && p.path("assigneeRule").asText().isBlank());
		} catch (Exception e) {
			return false;
		}
	}

	private void validateNode(WorkflowNode n) {
		required(n.getFlowId(), "flowId不能为空");
		required(n.getNodeKey(), "节点Key不能为空");
		required(n.getNodeName(), "节点名称不能为空");
		n.setNodeType(upper(required(n.getNodeType(), "节点类型不能为空")));
		if (!NODE_TYPES.contains(n.getNodeType()))
			throw new IllegalArgumentException("不支持的节点类型");
		validateJson(n.getPropertiesJson(), "节点扩展配置");
		validateJson(n.getAdditionalFormIds(), "附加表单");
		validateJson(n.getFieldPermissionsJson(), "字段权限");
		try {
			for (JsonNode id : json.readTree(n.getAdditionalFormIds() == null ? "[]" : n.getAdditionalFormIds()))
				validateForm(id.asText());
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("附加表单不是合法JSON");
		}
	}

	private void validateForm(String id) {
		if (id != null && !id.isBlank() && !formDefinitions.existsById(id))
			throw new IllegalArgumentException("引用的表单不存在");
	}

	private void validateJson(String value, String label) {
		if (value != null && !value.isBlank())
			try {
				json.readTree(value);
			} catch (Exception e) {
				throw new IllegalArgumentException(label + "不是合法JSON");
			}
	}

	private void validateEdge(WorkflowEdge e) {
		required(e.getFlowId(), "flowId不能为空");
		if (nodes.findByFlowIdAndNodeKey(e.getFlowId(), e.getFromNodeKey()).isEmpty()
				|| nodes.findByFlowIdAndNodeKey(e.getFlowId(), e.getToNodeKey()).isEmpty())
			throw new IllegalArgumentException("连线节点不存在");
		if (Objects.equals(e.getFromNodeKey(), e.getToNodeKey()))
			throw new IllegalArgumentException("节点不能直接连接自身");
		if (Boolean.TRUE.equals(e.getIsDefault()) && edges.findByFlowIdAndFromNodeKey(e.getFlowId(), e.getFromNodeKey())
				.stream().anyMatch(x -> Boolean.TRUE.equals(x.getIsDefault()) && !Objects.equals(x.getId(), e.getId())))
			throw new IllegalArgumentException("同一节点只能配置一条默认分支");
		if (!Boolean.TRUE.equals(e.getIsDefault()) && e.getConditionExpr() != null && !e.getConditionExpr().isBlank()
				&& !CONDITION.matcher(e.getConditionExpr().trim()).matches())
			throw new IllegalArgumentException("条件表达式不合法");
	}

	private Set<String> reachable(String start, List<WorkflowEdge> es) {
		Map<String, List<String>> g = new HashMap<>();
		for (WorkflowEdge e : es)
			g.computeIfAbsent(e.getFromNodeKey(), k -> new ArrayList<>()).add(e.getToNodeKey());
		Set<String> seen = new HashSet<>();
		Deque<String> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty()) {
			String x = q.remove();
			if (seen.add(x))
				q.addAll(g.getOrDefault(x, List.of()));
		}
		return seen;
	}

	private boolean hasCycle(List<WorkflowNode> ns, List<WorkflowEdge> es) {
		Map<String, Integer> degree = new HashMap<>();
		Map<String, List<String>> graph = new HashMap<>();
		for (WorkflowNode n : ns)
			degree.put(n.getNodeKey(), 0);
		for (WorkflowEdge e : es)
			if (degree.containsKey(e.getFromNodeKey()) && degree.containsKey(e.getToNodeKey())) {
				graph.computeIfAbsent(e.getFromNodeKey(), k -> new ArrayList<>()).add(e.getToNodeKey());
				degree.put(e.getToNodeKey(), degree.get(e.getToNodeKey()) + 1);
			}
		Deque<String> q = new ArrayDeque<>();
		degree.forEach((k, v) -> {
			if (v == 0)
				q.add(k);
		});
		int visited = 0;
		while (!q.isEmpty()) {
			String x = q.remove();
			visited++;
			for (String y : graph.getOrDefault(x, List.of()))
				if (degree.compute(y, (k, v) -> v - 1) == 0)
					q.add(y);
		}
		return visited < degree.size();
	}

	private WorkflowReview finding(String f, String n, String source, String severity, String category, String title,
			String desc, String suggestion, boolean blocking, WorkflowDefinition d, List<WorkflowNode> ns,
			List<WorkflowEdge> es) {
		WorkflowReview r = new WorkflowReview();
		r.setFlowId(f);
		r.setNodeId(n);
		r.setSource(source);
		r.setSeverity(severity);
		r.setCategory(category);
		r.setTitle(title);
		r.setDescription(desc);
		r.setSuggestion(suggestion);
		r.setBlocking(blocking);
		r.setConfigHash(hash(d, ns, es));
		return r;
	}

	private String hash(WorkflowDefinition d, List<WorkflowNode> ns, List<WorkflowEdge> es) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(json.writeValueAsString(List.of(d, ns, es)).getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return "";
		}
	}

	private WorkflowDefinition requireDefinition(String id) {
		return definitions.findById(id).orElseThrow(() -> new IllegalArgumentException("流程不存在"));
	}

	private WorkflowInstance requireInstance(String id) {
		return instances.findById(id).orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
	}

	private void ensureParticipant(WorkflowInstance instance, String actor) {
		boolean participant = tasks.findByInstanceIdOrderByCreateTimeAsc(instance.getId()).stream()
				.anyMatch(t -> actor.equals(t.getAssignee()));
		if (!actor.equals(instance.getInitiator()) && !participant)
			throw new AccessDeniedException("无权访问该流程表单");
	}

	private Map<String, Object> definitionView(WorkflowDefinition d) {
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("id", d.getId());
		v.put("flowCode", d.getFlowCode());
		v.put("flowName", d.getFlowName());
		v.put("category", d.getCategory());
		v.put("version", d.getVersion());
		v.put("description", d.getDescription() == null ? "" : d.getDescription());
		v.put("tags", d.getTags() == null ? "" : d.getTags());
		v.put("mainFormId", d.getMainFormId());
		return v;
	}

	private Map<String, Object> instanceView(WorkflowInstance i, WorkflowDefinition d) {
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("id", i.getId());
		v.put("definitionId", i.getDefinitionId());
		v.put("flowCode", d.getFlowCode());
		v.put("flowName", d.getFlowName());
		v.put("version", i.getDefinitionVersion());
		v.put("businessKey", i.getBusinessKey());
		v.put("initiator", i.getInitiator());
		v.put("status", i.getStatus());
		v.put("engineType", i.getEngineType());
		v.put("engineInstanceId", i.getEngineInstanceId());
		v.put("currentNodeKey", i.getCurrentNodeKey());
		v.put("createTime", i.getCreateTime());
		v.put("finishedAt", i.getFinishedAt());
		return v;
	}

	private Map<String, Object> taskView(WorkflowTask t) {
		WorkflowInstance i = instances.findById(t.getInstanceId()).orElse(null);
		WorkflowDefinition d = i == null
				? null
				: definitions.findById(i.getDefinitionId()).orElse(null);
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("id", t.getId());
		v.put("instanceId", t.getInstanceId());
		// 历史数据可能遗留已删除的实例或定义，单条孤儿记录不能拖垮整个待办/已办列表。
		v.put("flowName", d == null ? "历史流程" : d.getFlowName());
		v.put("flowCode", d == null ? null : d.getFlowCode());
		v.put("businessKey", i == null ? null : i.getBusinessKey());
		v.put("initiator", i == null ? null : i.getInitiator());
		v.put("instanceStatus", i == null ? "ARCHIVED" : i.getStatus());
		v.put("nodeKey", t.getNodeKey());
		v.put("nodeName", t.getNodeName());
		v.put("assignee", t.getAssignee());
		v.put("status", t.getStatus());
		v.put("engineTaskId", t.getEngineTaskId());
		v.put("claimable", "CLAIMABLE".equals(t.getStatus()));
		v.put("claimed", "PENDING".equals(t.getStatus()) && t.getAssignee() != null);
		v.put("taskKind", t.getTaskKind());
		v.put("comment", t.getComment());
		v.put("createTime", t.getCreateTime());
		v.put("dueAt", t.getDueAt());
		v.put("slaStatus", t.getSlaStatus());
		v.put("reminderCount", t.getReminderCount());
		v.put("escalationLevel", t.getEscalationLevel());
		v.put("completedAt", t.getCompletedAt());
		WorkflowNode node = i == null
				? null
				: nodes.findByFlowIdAndNodeKey(i.getDefinitionId(), t.getNodeKey()).orElse(null);
		if (node != null) {
			v.put("operations", taskOperationView(node, t));
			v.put("requiresFormInput", requiresFormInput(d, node));
			v.put("returnPolicy", nodeStringProperty(node, "returnPolicy", "PREVIOUS"));
			v.put("rejectPolicy", nodeStringProperty(node, "rejectPolicy", "TERMINATE"));
			v.put("returnTargets", returnTargetKeys(i.getId(), t.getNodeKey()).stream().map(key -> {
				Map<String, String> target = new LinkedHashMap<>();
				target.put("nodeKey", key);
				target.put("nodeName", nodes.findByFlowIdAndNodeKey(i.getDefinitionId(), key)
						.map(WorkflowNode::getNodeName).orElse(key));
				return target;
			}).toList());
		}
		return v;
	}

	private boolean requiresFormInput(WorkflowDefinition definition, WorkflowNode node) {
		if (definition == null) {
			return false;
		}
		Set<String> formIds = new HashSet<>(additionalForms(node));
		if (definition.getMainFormId() != null && !definition.getMainFormId().isBlank()) {
			formIds.add(definition.getMainFormId());
		}
		return permissionMap(node, "permissions").entrySet().stream()
				.anyMatch(entry -> "EDIT".equals(entry.getValue())
						&& formIds.stream().anyMatch(formId -> entry.getKey().startsWith(formId + ".")));
	}

	private Map<String, Object> historyView(WorkflowTask t) {
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("id", t.getId());
		v.put("nodeKey", t.getNodeKey());
		v.put("nodeName", t.getNodeName());
		v.put("assignee", t.getAssignee());
		v.put("status", t.getStatus());
		v.put("comment", t.getComment());
		v.put("createTime", t.getCreateTime());
		v.put("completedAt", t.getCompletedAt());
		return v;
	}

	private List<String> additionalForms(WorkflowNode node) {
		try {
			List<String> values = new ArrayList<>();
			for (JsonNode id : json.readTree(node.getAdditionalFormIds() == null ? "[]" : node.getAdditionalFormIds()))
				values.add(id.asText());
			return values;
		} catch (Exception e) {
			return List.of();
		}
	}

	private WorkflowTask currentPendingTask(String instanceId, String actor) {
		return tasks.findByInstanceIdOrderByCreateTimeAsc(instanceId).stream()
				.filter(task -> "PENDING".equals(task.getStatus()) && actor.equals(task.getAssignee()))
				.findFirst()
				.orElseThrow(() -> new AccessDeniedException("仅当前任务处理人可修改表单"));
	}

	/**
	 * 发起人修改任务只开放主表单；审批节点显式隐藏的系统字段继续保持隐藏，其他主表单字段恢复可编辑。
	 */
	private Map<String, String> starterReworkPermissions(
			WorkflowDefinition definition,
			WorkflowNode resumeNode) {
		Map<String, String> nodePermissions = permissionMap(resumeNode, "permissions");
		Map<String, String> permissions = new HashMap<>();
		if (definition.getMainFormId() == null || definition.getMainFormId().isBlank()) {
			return permissions;
		}
		for (FormField field : formService.fields(definition.getMainFormId())) {
			String key = definition.getMainFormId() + "." + field.getFieldKey();
			permissions.put(key, "HIDDEN".equals(nodePermissions.get(key)) ? "HIDDEN" : "EDIT");
		}
		return permissions;
	}

	private Map<String, String> permissionMap(WorkflowNode node, String field) {
		try {
			Map<String, String> values = new HashMap<>();
			json.readTree(node.getFieldPermissionsJson() == null ? "{}" : node.getFieldPermissionsJson()).path(field)
					.fields().forEachRemaining(e -> values.put(e.getKey(), e.getValue().asText()));
			return values;
		} catch (Exception e) {
			return Map.of();
		}
	}

	private Set<String> permissionSet(WorkflowNode node, String field) {
		try {
			Set<String> values = new HashSet<>();
			json.readTree(node.getFieldPermissionsJson() == null ? "{}" : node.getFieldPermissionsJson()).path(field)
					.fields().forEachRemaining(e -> {
						if (e.getValue().asBoolean())
							values.add(e.getKey());
					});
			return values;
		} catch (Exception e) {
			return Set.of();
		}
	}

	private Map<String, Object> runtimeForm(WorkflowInstance instance, String formId, String nodeKey, String role,
			Map<String, String> permissions, Set<String> required) {
		FormDefinition definition = formService.definition(formId);
		List<Map<String, Object>> schema = new ArrayList<>();
		for (FormField field : formService.fields(formId)) {
			String key = formId + "." + field.getFieldKey();
			String permission = permissions.getOrDefault(key, "READ");
			if (!"HIDDEN".equals(permission))
				schema.add(Map.of("fieldKey", field.getFieldKey(), "fieldLabel", field.getFieldLabel(), "fieldType",
						field.getFieldType(), "required", isRuntimeFieldRequired(field, key, permission, required),
						"permission", permission,
						"optionsJson", field.getOptionsJson() == null ? "[]" : field.getOptionsJson()));
		}
		Map<String, Object> storedData = formService.instance(instance.getId(), formId, nodeKey).map(x -> {
			try {
				return json.readValue(x.getDataJson(),
						new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
						});
			} catch (Exception e) {
				return Map.<String, Object>of();
			}
		}).orElse(Map.of());
		// HIDDEN 不只是前端隐藏控件：服务端也必须移除对应值，防止调用者直接读取响应 JSON。
		Map<String, Object> data = visibleRuntimeData(
				formId,
				formService.fields(formId),
				permissions,
				storedData);
		return Map.of("formId", formId, "formName", definition.getFormName(), "role", role, "fields", schema, "data",
				data);
	}

	Map<String, Object> visibleRuntimeData(
			String formId,
			List<FormField> fields,
			Map<String, String> permissions,
			Map<String, Object> storedData) {
		Map<String, Object> visible = new LinkedHashMap<>();
		for (FormField field : fields) {
			String permissionKey = formId + "." + field.getFieldKey();
			String permission = permissions.getOrDefault(permissionKey, "READ");
			if (!"HIDDEN".equals(permission)
					&& storedData.containsKey(field.getFieldKey())) {
				visible.put(field.getFieldKey(), storedData.get(field.getFieldKey()));
			}
		}
		return visible;
	}

	boolean isRuntimeFieldRequired(
			FormField field,
			String permissionKey,
			String permission,
			Set<String> nodeRequiredFields) {
		return nodeRequiredFields.contains(permissionKey)
				|| (Boolean.TRUE.equals(field.getRequired()) && "EDIT".equals(permission));
	}

	private void ensureDraft(WorkflowDefinition d) {
		if (!"DRAFT".equals(d.getStatus()))
			throw new IllegalArgumentException("只有草稿流程可以修改");
	}

	private String required(String v, String message) {
		if (v == null || v.isBlank())
			throw new IllegalArgumentException(message);
		return v.trim();
	}

	private String upper(String v) {
		return v == null ? "" : v.trim().toUpperCase();
	}

	private Map<String, Object> readMap(String value) {
		try {
			return json.readValue(
					value == null ? "{}" : value,
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
					});
		} catch (Exception exception) {
			throw new IllegalStateException("流程主表单数据无法解析", exception);
		}
	}
}
