package com.chronos.education.operationagent.service;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.support.CronExpression;
import com.chronos.agent.AgentRunStateMachine;
import com.chronos.agent.AgentRunStatus;
import com.chronos.education.operationagent.dao.*;
import com.chronos.education.operationagent.model.*;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.security.DirectoryDataScopeGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OperationAgentService {
	private static final java.util.regex.Pattern TERM_CREATION_PATTERN = java.util.regex.Pattern.compile(
			"(?:创建|新建|新增|建立|初始化|开设)(?:一个|新的|新)?\\s*(?:(?:20\\d{2}\\s*[-—至]\\s*20\\d{2}\\s*(?:学年)?\\s*(?:[-—]\\s*)?第?[一二12]学期)|(?:20\\d{2}\\s*(?:年度|学年)\\s*第?[一二12]学期)|学年学期|学期数据|新学期|学期)|"
					+ "(?:学年学期|学期数据|新学期|学期).{0,8}(?:创建|新建|新增|建立|初始化|开设)");
	private static final Set<String> ACTIVE_RUN_STATUSES = Set.of(
			"QUEUED", "RUNNING", "PAUSE_REQUESTED", "PAUSED", "REPLANNING",
			"WAITING_CONFIRMATION", "WAITING_WORKFLOW", "WAITING_APPROVAL");
	private final OperationAgentPlanRepository plans;
	private final OperationAgentPlanStepRepository planSteps;
	private final OperationAgentRunRepository runs;
	private final OperationAgentStepRunRepository stepRuns;
	private final OperationAgentEventRepository eventRepository;
	private final OperationAgentConditionRepository conditions;
	private final OperationAgentToolCatalog toolCatalog;
	private final OperationAgentGovernancePolicyService governance;
	private final OperationAgentEventService events;
	private final OperationAgentRunner runner;
	private final OperationAgentLlmService llm;
	private final OperationAgentNotificationService notifications;
	private final OperationAgentSemesterResolver semesterResolver;
	private final ObjectMapper mapper;
	private final IAuditLogService audit;
	private final DirectoryDataScopeGuard dataScopes;

	public OperationAgentService(
			OperationAgentPlanRepository plans,
			OperationAgentPlanStepRepository planSteps,
			OperationAgentRunRepository runs,
			OperationAgentStepRunRepository stepRuns,
			OperationAgentEventRepository eventRepository,
			OperationAgentConditionRepository conditions,
			OperationAgentToolCatalog toolCatalog,
			OperationAgentGovernancePolicyService governance,
			OperationAgentEventService events,
			OperationAgentRunner runner,
			OperationAgentLlmService llm,
			OperationAgentNotificationService notifications,
			OperationAgentSemesterResolver semesterResolver,
			ObjectMapper mapper,
			IAuditLogService audit,
			DirectoryDataScopeGuard dataScopes) {
		this.plans = plans;
		this.planSteps = planSteps;
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.eventRepository = eventRepository;
		this.conditions = conditions;
		this.toolCatalog = toolCatalog;
		this.governance = governance;
		this.events = events;
		this.runner = runner;
		this.llm = llm;
		this.notifications = notifications;
		this.semesterResolver = semesterResolver;
		this.mapper = mapper;
		this.audit = audit;
		this.dataScopes = dataScopes;
	}

	public OperationAgentLlmService.DraftResult draftPlan(
			OperationAgentLlmService.DraftCommand command,
			Authentication authentication) {
		return draftPlan(command, authentication, null);
	}

	private OperationAgentLlmService.DraftResult draftPlan(
			OperationAgentLlmService.DraftCommand command,
			Authentication authentication, com.fasterxml.jackson.databind.JsonNode intent) {
		if (command.organizationId() != null && !command.organizationId().isBlank()) {
			dataScopes.requireOrganization(authentication, command.organizationId());
			String qualifiedSemester = semesterResolver.qualify(
					command.semesterCode(), command.organizationId());
			command = new OperationAgentLlmService.DraftCommand(
					command.request(), command.knowledgeBaseId(), qualifiedSemester, command.admissionYear(),
					command.managedFileId(), command.managedFileIds(), command.candidateOnly(), command.organizationId());
		}
		OperationAgentLlmService.DraftResult result = llm.draft(command, authentication, intent);
		audit.log(authentication.getName(), "OPERATION_AGENT_LLM_DRAFT",
				"stepCount=" + result.plan().steps().size());
		return result;
	}

	/**
	 * Conversational entry point: the user provides an outcome, while the agent
	 * creates the internal plan snapshot and starts a candidate run atomically.
	 * The plan remains an auditable implementation detail and is not a form the
	 * user has to design manually.
	 */
	@Transactional
	public ConversationResult converse(ConversationCommand command,
			Authentication authentication) {
		String conversationRequest = conversationRequest(command.contextMessages(), command.request());
		if (command.organizationId() != null && !command.organizationId().isBlank()) {
			dataScopes.requireOrganization(authentication, command.organizationId());
		}
		var intent = llm.recognizeConversation(conversationRequest, authentication);
		if (intent.path("cancelled").asBoolean()) {
			return new ConversationResult("CANCELLED", null, null, null, null);
		}
		if (intent.path("needsClarification").asBoolean() || intent.path("skillCodes").isEmpty()) {
			return new ConversationResult("NEED_INTENT", null, null, null,
					new OperationAgentSemesterResolver.Resolution("NEED_INTENT", null, null, null,
							intent.path("question").asText("请补充本次要完成的具体业务目标。"), false, false));
		}
		boolean termCreationRequested = intent.path("createTerm").asBoolean()
				|| (Boolean.TRUE.equals(command.createMissingTerm()) && intent.path("confirmed").asBoolean());
		String recognizedTerm = intent.path("semesterCode").asText(null);
		if (recognizedTerm == null) recognizedTerm = command.semesterCode();
		OperationAgentSemesterResolver.Resolution resolution = intent.path("requiresSemester").asBoolean()
				? semesterResolver.resolve(
						"", recognizedTerm, intent.path("confirmed").asBoolean(),
						termCreationRequested, command.organizationId())
				: new OperationAgentSemesterResolver.Resolution(
						"READY", null, null, null, "本次业务不依赖学期，按通用业务流程处理。", false, false);
		if (!"READY".equals(resolution.status())) {
			return new ConversationResult(resolution.status(), null, null, null, resolution);
		}
		String planningRequest = resolution.creationRequested()
				? "创建学年学期数据。" + conversationRequest
				: conversationRequest;
		planningRequest += "\nLLM已识别的结构化意图：" + intent.toString();
		OperationAgentLlmService.DraftCommand draftCommand = new OperationAgentLlmService.DraftCommand(
				planningRequest, command.knowledgeBaseId(), resolution.termCode(), command.admissionYear(),
				command.managedFileId(), command.managedFileIds(), command.candidateOnly(), command.organizationId());
		boolean academicOnly = intent.path("skillCodes").size() == 1
				&& "academic.term-setup".equals(intent.path("skillCodes").get(0).asText());
		OperationAgentLlmService.DraftResult draft = academicOnly
				? llm.academicIntentDraft(intent, draftCommand) : draftPlan(draftCommand, authentication, intent);
		llm.validateIntentTools(intent, draft);
		if (!draft.readyToSave()) {
			return new ConversationResult("NEED_INPUT", draft, null, null, resolution);
		}
		PlanView plan = savePlan(null, draft.plan(), authentication);
		RunView run = start(plan.plan().getId(), authentication);
		return new ConversationResult("STARTED", draft, plan, run, resolution);
	}

	private boolean requiresSemesterContext(ConversationCommand command, String conversationRequest) {
		if (command.semesterCode() != null && !command.semesterCode().isBlank()) return true;
		String request = conversationRequest.toLowerCase(Locale.ROOT);
		return List.of("学期", "排课", "课表", "开课", "教学任务", "选课", "考试", "成绩",
				"期末", "住宿", "宿舍", "床位", "毕业", "归档", "新生", "录取", "入学", "分班")
				.stream().anyMatch(request::contains);
	}

	private boolean requestsTermCreation(String request) {
		return TERM_CREATION_PATTERN.matcher(request == null ? "" : request).find();
	}

	/**
	 * 对话端传入的上下文只用于本次意图和参数合并。服务端限制轮数与长度，
	 * 避免客户端无限回传历史内容导致模型上下文和审计记录失控。
	 */
	private String conversationRequest(List<String> contextMessages, String request) {
		String current = request == null ? "" : request.trim();
		if (current.isBlank()) throw new IllegalArgumentException("请描述希望智能体完成的工作");
		List<String> values = new java.util.ArrayList<>();
		if (contextMessages != null) {
			contextMessages.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
					.limit(12).forEach(value -> values.add(value.length() > 1000 ? value.substring(0, 1000) : value));
		}
		values.add(current.length() > 1000 ? current.substring(0, 1000) : current);
		String merged = String.join("\n用户补充：", values);
		if (merged.length() > 6000) throw new IllegalArgumentException("对话上下文过长，请开始一个新任务");
		return merged;
	}

	@Transactional(readOnly = true)
	public List<PlanView> listPlans(Authentication authentication) {
		String username = authentication.getName();
		return plans.findAllByOrderByCreateTimeDesc().stream()
				.filter(value -> "ALL".equals(value.getVisibility()) || username.equals(value.getOwnerUsername()))
				.map(this::view).toList();
	}

	@Transactional
	public PlanView savePlan(String id, PlanCommand command, Authentication authentication) {
		validate(command);
		OperationAgentPlan plan = id == null ? new OperationAgentPlan()
				: plans.findById(id).orElseThrow(() -> new IllegalArgumentException("执行计划不存在"));
		if (id != null && runs.existsByPlanIdAndStatusIn(id, ACTIVE_RUN_STATUSES)) {
			throw new IllegalStateException("执行计划存在进行中的运行实例，请完成或取消后再编辑");
		}
		if (id != null && !authentication.getName().equals(plan.getOwnerUsername())
				&& !"SYSTEM".equals(plan.getOwnerUsername())) {
			throw new IllegalStateException("只能编辑本人创建的执行计划");
		}
		plan.setPlanName(command.planName().trim());
		plan.setObjective(command.objective().trim());
		plan.setRuleText(command.ruleText().trim());
		plan.setCycleType(normalize(command.cycleType(), "MANUAL"));
		plan.setCronExpression(blank(command.cronExpression()));
		plan.setPeriodStart(command.periodStart());
		plan.setPeriodEnd(command.periodEnd());
		plan.setKnowledgeBaseId(blank(command.knowledgeBaseId()));
		plan.setVisibility(normalize(command.visibility(), "OWNER"));
		plan.setEnabled(command.enabled() == null || command.enabled());
		plan.setNextScheduledAt(nextScheduledAt(
				plan.getCycleType(), plan.getCronExpression(), plan.getPeriodStart(), plan.getPeriodEnd(), LocalDateTime.now()));
		plan.setOwnerUsername(authentication.getName());
		if (id != null) plan.setPlanVersion(plan.getPlanVersion() + 1);
		List<String> authorized = command.authorizedTools().stream().distinct().toList();
		for (String tool : authorized) toolCatalog.require(tool);
		plan.setAuthorizedToolsJson(write(authorized));
		plan = plans.save(plan);
		if (id != null) {
			planSteps.deleteByPlanId(id);
			// Hibernate may otherwise order the replacement INSERTs before orphan DELETEs,
			// violating the unique (plan_id, step_order) constraint during plan editing.
			planSteps.flush();
		}
		int order = 1;
		for (StepCommand value : command.steps()) {
			OperationAgentToolCatalog.ToolDefinition definition = toolCatalog.require(value.toolCode());
			if (!authorized.contains(value.toolCode())) {
				throw new IllegalArgumentException("步骤工具未加入计划授权范围：" + value.toolCode());
			}
			OperationAgentPlanStep step = new OperationAgentPlanStep();
			step.setPlanId(plan.getId());
			step.setStepOrder(order++);
			step.setStepCode(normalize(value.stepCode(), "STEP_" + (order - 1)));
			step.setStepName(value.stepName().trim());
			step.setDescription(blank(value.description()));
			step.setToolCode(value.toolCode());
			step.setBusinessStageOrder(definition.businessStageOrder());
			step.setBusinessStageCode(definition.businessStageCode());
			step.setBusinessStageName(definition.businessStageName());
			step.setRiskLevel(definition.riskLevel());
			OperationAgentGovernancePolicyService.GovernanceDecision decision = governance.decide(
					definition, value.toolConfig() == null ? Map.of() : value.toolConfig());
			step.setCheckpointMode(decision.mode());
			step.setPolicyCode(decision.policyCode());
			step.setPolicyVersion(decision.policyVersion());
			step.setApprovalRequired(decision.mode().requiresCheckpoint());
			step.setSuccessCriteria(blank(value.successCriteria()));
			step.setToolConfigJson(write(value.toolConfig() == null ? Map.of() : value.toolConfig()));
			planSteps.save(step);
		}
		audit.log(authentication.getName(), id == null ? "OPERATION_AGENT_PLAN_CREATE" : "OPERATION_AGENT_PLAN_UPDATE",
				"planId=" + plan.getId() + ", version=" + plan.getPlanVersion());
		return view(plan);
	}

	@Transactional
	public void deletePlan(String id, Authentication authentication) {
		OperationAgentPlan plan = plans.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("执行计划不存在"));
		if (!authentication.getName().equals(plan.getOwnerUsername())) {
			throw new IllegalStateException("只能删除本人创建的执行计划");
		}
		if (runs.existsByPlanIdAndStatusIn(id, ACTIVE_RUN_STATUSES)) {
			throw new IllegalStateException("执行计划正在运行，不能删除");
		}
		planSteps.deleteByPlanId(id);
		plans.delete(plan);
		audit.log(authentication.getName(), "OPERATION_AGENT_PLAN_DELETE", "planId=" + id);
	}

	@Transactional
	public RunView start(String planId, Authentication authentication) {
		OperationAgentPlan plan = plans.findById(planId)
				.filter(value -> Boolean.TRUE.equals(value.getEnabled()))
				.orElseThrow(() -> new IllegalArgumentException("执行计划不存在或已停用"));
		if (!"ALL".equals(plan.getVisibility()) && !authentication.getName().equals(plan.getOwnerUsername())) {
			throw new IllegalStateException("无权执行该计划");
		}
		return startPlan(plan, authentication.getName());
	}

	@Transactional
	public RunView startScheduled(String planId) {
		OperationAgentPlan plan = plans.findLockedById(planId)
				.filter(value -> Boolean.TRUE.equals(value.getEnabled()) && "CRON".equals(value.getCycleType()))
				.orElseThrow(() -> new IllegalArgumentException("周期执行计划不存在或已停用"));
		LocalDateTime now = LocalDateTime.now();
		if (plan.getNextScheduledAt() == null || plan.getNextScheduledAt().isAfter(now)) {
			throw new IllegalStateException("执行计划尚未到达调度时间");
		}
		RunView result = startPlan(plan, plan.getOwnerUsername());
		plan.setLastScheduledAt(now);
		plan.setNextScheduledAt(nextScheduledAt(
				plan.getCycleType(), plan.getCronExpression(), plan.getPeriodStart(), plan.getPeriodEnd(), now));
		plans.save(plan);
		return result;
	}

	private RunView startPlan(OperationAgentPlan plan, String username) {
		String planId = plan.getId();
		List<OperationAgentPlanStep> steps = planSteps.findByPlanIdOrderByStepOrder(planId);
		if (steps.isEmpty()) throw new IllegalStateException("执行计划没有可执行步骤");
		OperationAgentRun run = new OperationAgentRun();
		run.setPlanId(planId);
		run.setPlanVersion(plan.getPlanVersion());
		run.setOwnerUsername(username);
		run.setStatus("QUEUED");
		run.setNextAction("等待执行器载入计划");
		run.setPlanSnapshotJson(snapshot(plan, steps));
		run = runs.save(run);
		for (OperationAgentPlanStep source : steps) {
			OperationAgentStepRun step = new OperationAgentStepRun();
			step.setRunId(run.getId());
			step.setPlanStepId(source.getId());
			step.setStepOrder(source.getStepOrder());
			step.setStepName(source.getStepName());
			step.setToolCode(source.getToolCode());
			step.setBusinessStageOrder(source.getBusinessStageOrder());
			step.setBusinessStageCode(source.getBusinessStageCode());
			step.setBusinessStageName(source.getBusinessStageName());
			step.setApprovalRequired(source.getApprovalRequired());
			step.setCheckpointMode(source.getCheckpointMode());
			step.setPolicyCode(source.getPolicyCode());
			step.setPolicyVersion(source.getPolicyVersion());
			stepRuns.save(step);
		}
		OperationAgentCondition initial = new OperationAgentCondition();
		initial.setRunId(run.getId());
		initial.setConditionVersion(1);
		initial.setConditionText(plan.getRuleText());
		initial.setImpactAnalysis("计划启动时冻结的基础规则");
		initial.setParsedConditionJson("{}");
		initial.setSubmittedBy(username);
		conditions.save(initial);
		events.append(run.getId(), "RUN_CREATED", "INFO", "执行实例已创建",
				"已冻结计划第 " + plan.getPlanVersion() + " 版及工具授权范围。", null);
		audit.log(username, "OPERATION_AGENT_RUN_START", "runId=" + run.getId());
		String runId = run.getId();
		submitAfterCommit(runId);
		return runView(run);
	}

	@Transactional(readOnly = true)
	public List<RunView> listRuns(Authentication authentication) {
		List<OperationAgentRun> visible = hasReviewPermission(authentication)
				? runs.findTop30ByOrderByCreateTimeDesc()
				: runs.findTop30ByOwnerUsernameOrderByCreateTimeDesc(authentication.getName());
		return visible.stream()
				.map(this::runView).toList();
	}

	@Transactional(readOnly = true)
	public RunDetail detail(String id, Authentication authentication) {
		OperationAgentRun run = ownedRun(id, authentication);
		CheckpointView checkpoint = checkpointView(run, authentication);
		return new RunDetail(
				runView(run),
				stepRuns.findByRunIdOrderByStepOrder(id),
				conditions.findByRunIdOrderByConditionVersion(id),
				candidatePromotionAvailable(run),
				checkpoint != null && checkpoint.mode() == OperationAgentCheckpointMode.DUAL_CONTROL
						? notifications.eligibleReviewers(run.getOwnerUsername()) : List.of(),
				checkpoint);
	}

	/**
	 * Confirms a completed candidate run and creates a traceable formal follow-up
	 * run. The frozen candidate snapshot is reused, while write tools have dryRun
	 * removed and regain their platform approval checkpoints.
	 */
	@Transactional
	public PromotionResult promoteCandidate(String id, Authentication authentication) {
		OperationAgentRun sourceRun = lockedOwnedRun(id, authentication);
		if (!"COMPLETED".equals(sourceRun.getStatus())) {
			throw new IllegalStateException("只有已完成的候选运行可以确认进入正式处理");
		}
		if (!candidatePromotionAvailable(sourceRun)) {
			throw new IllegalStateException("当前运行不包含可转正式处理的候选结果");
		}
		OperationAgentPlan sourcePlan = plans.findById(sourceRun.getPlanId())
				.orElseThrow(() -> new IllegalStateException("候选计划已删除，无法继续正式处理"));
		List<StepCommand> formalSteps = new ArrayList<>();
		for (OperationAgentPlanStep source : planSteps.findByPlanIdOrderByStepOrder(sourcePlan.getId())) {
			OperationAgentToolCatalog.ToolDefinition definition = toolCatalog.require(source.getToolCode());
			Map<String, Object> config = stepSnapshotConfig(sourceRun, source.getStepOrder());
			if (definition.writeTool()) config.put("dryRun", false);
			formalSteps.add(new StepCommand(
					source.getStepCode(), source.getStepName(), source.getDescription(), source.getToolCode(),
					definition.approvalRequired(), source.getSuccessCriteria(), config));
		}
		PlanCommand command = new PlanCommand(
				sourcePlan.getPlanName() + " · 正式处理",
				sourcePlan.getObjective(),
				sourcePlan.getRuleText() + "\n候选运行 " + id + " 已由 " + authentication.getName() + " 确认，正式写入仍遵守权限和审批检查点。",
				"MANUAL", null, sourcePlan.getPeriodStart(), sourcePlan.getPeriodEnd(),
				sourcePlan.getKnowledgeBaseId(), sourcePlan.getVisibility(), true,
				readList(sourcePlan.getAuthorizedToolsJson()), formalSteps);
		PlanView formalPlan = savePlan(null, command, authentication);
		RunView formalRun = start(formalPlan.plan().getId(), authentication);
		// Carry the candidate evidence into the formal run. Reviewers must be able
		// to inspect the exact rows and calculated assignments before approving a
		// write checkpoint; an approval without its candidate evidence is invalid.
		Map<Integer, OperationAgentStepRun> candidateSteps = stepRuns
				.findByRunIdOrderByStepOrder(id).stream()
				.collect(java.util.stream.Collectors.toMap(
						OperationAgentStepRun::getStepOrder, value -> value));
		for (OperationAgentStepRun target : stepRuns.findByRunIdOrderByStepOrder(formalRun.run().getId())) {
			OperationAgentStepRun source = candidateSteps.get(target.getStepOrder());
			if (source == null || source.getResultJson() == null || source.getResultJson().isBlank()) continue;
			target.setResultJson(source.getResultJson());
			target.setMessage("等待执行；候选结果已附在审核检查点供核对");
			stepRuns.save(target);
		}
		events.append(id, "CANDIDATE_CONFIRMED", "SUCCESS", "候选结果已确认",
				authentication.getName() + " 已确认候选结果并发起正式处理运行 " + formalRun.run().getId() + "。", null);
		audit.log(authentication.getName(), "OPERATION_AGENT_CANDIDATE_PROMOTE",
				"sourceRunId=" + id + ", formalRunId=" + formalRun.run().getId());
		return new PromotionResult(formalPlan, formalRun);
	}

	@Transactional(readOnly = true)
	public List<OperationAgentEvent> events(String id, long after, Authentication authentication) {
		ownedRun(id, authentication);
		return eventRepository.findTop200ByRunIdAndEventSequenceGreaterThanOrderByEventSequence(
				id, Math.max(0, after));
	}

	@Transactional
	public RunView requestPause(String id, Authentication authentication) {
		OperationAgentRun run = lockedOwnedRun(id, authentication);
		if (Set.of("WAITING_CONFIRMATION", "WAITING_APPROVAL", "WAITING_WORKFLOW")
				.contains(run.getStatus())) {
			AgentRunStateMachine.assertTransition(
					AgentRunStatus.valueOf(run.getStatus()), AgentRunStatus.PAUSED);
			run.setStatus("PAUSED");
			run.setPausedAt(LocalDateTime.now());
			run.setNextAction("可补充附加条件并评估影响，或继续到审批检查点");
			runs.save(run);
			events.append(id, "RUN_PAUSED", "WARN", "执行已暂停", "当前没有运行中的写入操作。", null);
		} else if ("RUNNING".equals(run.getStatus())) {
			AgentRunStateMachine.assertTransition(AgentRunStatus.RUNNING, AgentRunStatus.PAUSE_REQUESTED);
			run.setStatus("PAUSE_REQUESTED");
			run.setPauseRequested(true);
			run.setNextAction("等待当前步骤结束并进入安全检查点");
			runs.save(run);
			events.append(id, "PAUSE_REQUESTED", "WARN", "已请求暂停",
					"当前步骤会先完成，智能体不会在事务中间强制中断。", null);
		} else {
			throw new IllegalStateException("当前状态不能暂停");
		}
		return runView(run);
	}

	@Transactional
	public RunView resume(String id, Authentication authentication) {
		OperationAgentRun run = lockedOwnedRun(id, authentication);
		if (!Set.of("PAUSED", "WAITING_CONFIRMATION", "WAITING_APPROVAL", "WAITING_WORKFLOW")
				.contains(run.getStatus())) {
			throw new IllegalStateException("只有已暂停或等待确认的实例可以继续");
		}
		if (!"PAUSED".equals(run.getStatus())) {
			throw new IllegalStateException("当前步骤存在待处理检查点，请先完成确认或业务审批");
		}
		AgentRunStateMachine.assertTransition(AgentRunStatus.PAUSED, AgentRunStatus.QUEUED);
		run.setStatus("QUEUED");
		run.setPauseRequested(false);
		run.setNextAction("等待执行器从检查点继续");
		runs.save(run);
		events.append(id, "RUN_RESUMED", "INFO", "已继续执行", "执行器将从首个未完成步骤继续。", null);
		submitAfterCommit(id);
		return runView(run);
	}

	@Transactional
	public RunView approve(String id, ReviewCommand command, Authentication authentication) {
		if (!hasReviewPermission(authentication)) {
			throw new IllegalStateException("当前账号没有学期运营智能体审核权限");
		}
		OperationAgentRun run = runs.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
		if (authentication.getName().equals(run.getOwnerUsername())) {
			throw new IllegalStateException("计划负责人不能审核自己的智能体步骤");
		}
		if (!Set.of("WAITING_APPROVAL", "PAUSED").contains(run.getStatus())) {
			throw new IllegalStateException("当前没有等待确认的步骤");
		}
		OperationAgentStepRun step = stepRuns.findByRunIdAndStepOrder(id, run.getCurrentStepOrder())
				.orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
		if (step.getCheckpointMode() != OperationAgentCheckpointMode.DUAL_CONTROL) {
			throw new IllegalStateException("当前步骤不是异人审核检查点");
		}
		if (!"WAITING_APPROVAL".equals(step.getStatus())) {
			throw new IllegalStateException("当前步骤不需要确认");
		}
		Map<String, Object> evidence = readMap(step.getResultJson());
		if (evidence.get("errors") instanceof Collection<?> errors && !errors.isEmpty()) {
			throw new IllegalStateException("候选结果存在校验错误，不能审核通过；请修正名册后重新执行");
		}
		step.setApprovedBy(authentication.getName());
		step.setApprovedAt(LocalDateTime.now());
		step.setDecisionAction("APPROVE");
		step.setDecisionComment(blank(command == null ? null : command.comment()));
		step.setStatus("QUEUED");
		step.setMessage("人工已确认，等待执行");
		stepRuns.save(step);
		AgentRunStateMachine.assertTransition(AgentRunStatus.valueOf(run.getStatus()), AgentRunStatus.QUEUED);
		run.setStatus("QUEUED");
		run.setPauseRequested(false);
		run.setNextAction("已批准，等待执行器调用业务工具");
		runs.save(run);
		events.append(id, "STEP_APPROVED", "SUCCESS", "步骤已获人工确认",
				authentication.getName() + " 已批准“" + step.getStepName() + "”。"
						+ reviewComment(command), null);
		audit.log(authentication.getName(), "OPERATION_AGENT_STEP_APPROVE",
				"runId=" + id + ", stepOrder=" + step.getStepOrder());
		submitAfterCommit(id);
		return runView(run);
	}

	/**
	 * 统一检查点决策入口。本人确认和异人审核共享证据版本校验，但权限规则
	 * 由检查点模式决定，前端不能通过更换 action 绕过平台策略。
	 */
	@Transactional
	public RunView decideCheckpoint(
			String id,
			Integer stepOrder,
			CheckpointDecisionCommand command,
			Authentication authentication) {
		if (command == null || command.action() == null || command.action().isBlank()) {
			throw new IllegalArgumentException("检查点决策不能为空");
		}
		String action = command.action().trim().toUpperCase(Locale.ROOT);
		if (Set.of("APPROVE", "REJECT").contains(action)) {
			OperationAgentRun reviewRun = runs.findLockedById(id)
					.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
			OperationAgentStepRun reviewStep = stepRuns
					.findByRunIdAndStepOrder(id, reviewRun.getCurrentStepOrder())
					.orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
			if (stepOrder == null || !reviewStep.getStepOrder().equals(stepOrder)) {
				throw new IllegalStateException("检查点步骤已变化，请刷新后重新审核");
			}
			if (reviewStep.getCheckpointMode() != OperationAgentCheckpointMode.DUAL_CONTROL
					|| !"WAITING_APPROVAL".equals(reviewStep.getStatus())) {
				throw new IllegalStateException("当前步骤不是异人审核检查点");
			}
			validateEvidenceVersion(reviewStep, command);
			return "APPROVE".equals(action)
					? approve(id, new ReviewCommand(command.comment()), authentication)
					: reject(id, new ReviewCommand(command.comment()), authentication);
		}
		if ("CANCEL".equals(action)) return cancel(id, authentication);
		if (!"CONFIRM".equals(action)) throw new IllegalArgumentException("不支持的检查点决策：" + action);

		OperationAgentRun run = lockedOwnedRun(id, authentication);
		if (!"WAITING_CONFIRMATION".equals(run.getStatus())) {
			throw new IllegalStateException("当前没有等待发起人确认的步骤");
		}
		OperationAgentStepRun step = stepRuns.findByRunIdAndStepOrder(id, run.getCurrentStepOrder())
				.orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
		if (stepOrder == null || !step.getStepOrder().equals(stepOrder)) {
			throw new IllegalStateException("检查点步骤已变化，请刷新后重新确认");
		}
		if (step.getCheckpointMode() != OperationAgentCheckpointMode.INITIATOR_CONFIRM
				|| !"WAITING_CONFIRMATION".equals(step.getStatus())) {
			throw new IllegalStateException("当前步骤不是发起人确认检查点");
		}
		validateEvidenceVersion(step, command);
		validateEvidenceHasNoErrors(step);

		step.setApprovedBy(authentication.getName());
		step.setApprovedAt(LocalDateTime.now());
		step.setDecisionAction("CONFIRM");
		step.setDecisionComment(blank(command.comment()));
		step.setStatus("QUEUED");
		step.setMessage("发起人已确认候选结果，等待执行");
		stepRuns.save(step);
		AgentRunStateMachine.assertTransition(
				AgentRunStatus.WAITING_CONFIRMATION, AgentRunStatus.QUEUED);
		run.setStatus("QUEUED");
		run.setPauseRequested(false);
		run.setNextAction("已确认候选结果，等待执行器调用正式业务工具");
		runs.save(run);
		events.append(id, "STEP_CONFIRMED", "SUCCESS", "发起人已确认写入",
				authentication.getName() + " 已确认“" + step.getStepName() + "”候选结果。"
						+ reviewComment(new ReviewCommand(command.comment())), null);
		audit.log(authentication.getName(), "OPERATION_AGENT_STEP_CONFIRM",
				"runId=" + id + ", stepOrder=" + step.getStepOrder()
						+ ", evidenceVersion=" + step.getEvidenceVersion());
		submitAfterCommit(id);
		return runView(run);
	}

	private void validateEvidenceVersion(
			OperationAgentStepRun step,
			CheckpointDecisionCommand command) {
		if (step.getEvidenceHash() == null || step.getEvidenceHash().isBlank()) {
			throw new IllegalStateException("候选证据需要刷新，请刷新后重新确认");
		}
		if (!Objects.equals(step.getEvidenceVersion(), command.evidenceVersion())
				|| !Objects.equals(step.getEvidenceHash(), command.evidenceHash())) {
			throw new IllegalStateException("候选结果已发生变化，请刷新后重新确认");
		}
	}

	private void validateEvidenceHasNoErrors(OperationAgentStepRun step) {
		Map<String, Object> evidence = readMap(step.getResultJson());
		if (evidence.get("errors") instanceof Collection<?> errors && !errors.isEmpty()) {
			throw new IllegalStateException("候选结果存在校验错误，不能确认写入；请修正业务文件后重新执行");
		}
		if (Boolean.FALSE.equals(evidence.get("valid"))) {
			throw new IllegalStateException("候选结果校验未通过，不能确认写入");
		}
	}

	@Transactional
	public RunDetail refreshApprovalEvidence(String id, Authentication authentication) {
		OperationAgentRun run = runs.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
		OperationAgentStepRun step = stepRuns.findByRunIdAndStepOrder(id, run.getCurrentStepOrder())
				.orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
		if (step.getCheckpointMode() == OperationAgentCheckpointMode.INITIATOR_CONFIRM) {
			if (!authentication.getName().equals(run.getOwnerUsername())) {
				throw new IllegalStateException("只有任务发起人可以刷新本人确认候选结果");
			}
		} else {
			if (!hasReviewPermission(authentication)) {
				throw new IllegalStateException("当前账号没有学期运营智能体审核权限");
			}
			if (authentication.getName().equals(run.getOwnerUsername())) {
				throw new IllegalStateException("计划负责人不能刷新异人审核候选结果");
			}
		}
		runner.refreshApprovalEvidence(id);
		audit.log(authentication.getName(), "OPERATION_AGENT_APPROVAL_EVIDENCE_REFRESH",
				"runId=" + id + ", stepOrder=" + run.getCurrentStepOrder());
		return detail(id, authentication);
	}

	/** Rejects a write checkpoint and returns the run to its owner for correction. */
	@Transactional
	public RunView reject(String id, ReviewCommand command, Authentication authentication) {
		if (!hasReviewPermission(authentication)) {
			throw new IllegalStateException("当前账号没有学期运营智能体审核权限");
		}
		String comment = command == null || command.comment() == null ? "" : command.comment().trim();
		if (comment.isBlank()) throw new IllegalArgumentException("驳回时必须填写审核意见");
		OperationAgentRun run = runs.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
		if (authentication.getName().equals(run.getOwnerUsername())) {
			throw new IllegalStateException("计划负责人不能审核自己的智能体步骤");
		}
		if (!Set.of("WAITING_APPROVAL", "PAUSED").contains(run.getStatus())) {
			throw new IllegalStateException("当前没有等待确认的步骤");
		}
		OperationAgentStepRun step = stepRuns.findByRunIdAndStepOrder(id, run.getCurrentStepOrder())
				.orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
		if (step.getCheckpointMode() != OperationAgentCheckpointMode.DUAL_CONTROL) {
			throw new IllegalStateException("当前步骤不是异人审核检查点");
		}
		if (!"WAITING_APPROVAL".equals(step.getStatus())) {
			throw new IllegalStateException("当前步骤不需要确认");
		}
		step.setStatus("FAILED");
		step.setProgress(100);
		step.setApprovedBy(authentication.getName());
		step.setApprovedAt(LocalDateTime.now());
		step.setDecisionAction("REJECT");
		step.setDecisionComment(comment);
		step.setMessage("人工审核已驳回：" + comment);
		step.setCompletedAt(LocalDateTime.now());
		stepRuns.save(step);
		AgentRunStateMachine.assertTransition(AgentRunStatus.valueOf(run.getStatus()), AgentRunStatus.FAILED);
		run.setStatus("FAILED");
		run.setErrorMessage("人工审核已驳回：" + comment);
		run.setSummary("步骤“" + step.getStepName() + "”未通过人工审核");
		run.setNextAction("请计划负责人修正数据或附加条件后重试");
		run.setCompletedAt(LocalDateTime.now());
		runs.save(run);
		events.append(id, "STEP_REJECTED", "WARN", "步骤被人工驳回",
				authentication.getName() + " 驳回“" + step.getStepName() + "”：" + comment,
				step.getResultJson());
		audit.log(authentication.getName(), "OPERATION_AGENT_STEP_REJECT",
				"runId=" + id + ", stepOrder=" + step.getStepOrder());
		notifications.failed(run);
		return runView(run);
	}

	private String reviewComment(ReviewCommand command) {
		String comment = command == null || command.comment() == null ? "" : command.comment().trim();
		return comment.isBlank() ? "" : " 审核意见：" + comment;
	}

	private CheckpointView checkpointView(OperationAgentRun run, Authentication authentication) {
		if (run.getCurrentStepOrder() == null) return null;
		OperationAgentStepRun step = stepRuns
				.findByRunIdAndStepOrder(run.getId(), run.getCurrentStepOrder()).orElse(null);
		if (step == null || !Set.of("WAITING_CONFIRMATION", "WAITING_APPROVAL", "WAITING_WORKFLOW")
				.contains(step.getStatus())) return null;
		OperationAgentCheckpointMode mode = step.getCheckpointMode();
		boolean owner = authentication.getName().equals(run.getOwnerUsername());
		String label = switch (mode) {
			case INITIATOR_CONFIRM -> "等待本人确认";
			case DUAL_CONTROL -> "等待独立审核";
			case BUSINESS_WORKFLOW -> "等待业务流程处理";
			case NONE -> "无需确认";
		};
		String reason = switch (mode) {
			case INITIATOR_CONFIRM -> "批量写入正式数据前需要发起人核对候选结果。";
			case DUAL_CONTROL -> "该步骤必须由具备审核权限的非发起人复核。";
			case BUSINESS_WORKFLOW -> "该步骤由流程中心任务办理人处理。";
			case NONE -> "当前步骤不需要人工检查点。";
		};
		List<String> actions = new ArrayList<>();
		if (mode == OperationAgentCheckpointMode.INITIATOR_CONFIRM && owner) {
			actions.add("CONFIRM");
			actions.add("CANCEL");
		}
		if (mode == OperationAgentCheckpointMode.DUAL_CONTROL && !owner && hasReviewPermission(authentication)) {
			actions.add("APPROVE");
			actions.add("REJECT");
		}
		return new CheckpointView(mode, label, reason,
				mode == OperationAgentCheckpointMode.INITIATOR_CONFIRM && owner,
				mode == OperationAgentCheckpointMode.DUAL_CONTROL && !owner && hasReviewPermission(authentication),
				List.copyOf(actions), step.getEvidenceVersion(), step.getEvidenceHash());
	}

	@Transactional
	public RunView addCondition(String id, ConditionCommand command, Authentication authentication) {
		if (command.conditionText() == null || command.conditionText().isBlank()) {
			throw new IllegalArgumentException("附加条件不能为空");
		}
		OperationAgentRun run = lockedOwnedRun(id, authentication);
		String originalStatus = run.getStatus();
		if (!Set.of("RUNNING", "PAUSE_REQUESTED", "PAUSED", "WAITING_CONFIRMATION",
				"WAITING_WORKFLOW", "WAITING_APPROVAL").contains(originalStatus)) {
			throw new IllegalStateException("当前运行状态不能增加附加条件");
		}
		if ("RUNNING".equals(originalStatus)) {
			AgentRunStateMachine.assertTransition(AgentRunStatus.RUNNING, AgentRunStatus.PAUSE_REQUESTED);
			run.setStatus("PAUSE_REQUESTED");
			run.setPauseRequested(true);
		}
		int nextVersion = run.getConditionVersion() + 1;
		Map<String, Object> parsed = llm.interpretCondition(command.conditionText(), authentication.getName());
		OperationAgentCondition condition = new OperationAgentCondition();
		condition.setRunId(id);
		condition.setConditionVersion(nextVersion);
		condition.setConditionText(command.conditionText().trim());
		condition.setImpactAnalysis(impactAnalysis(parsed));
		condition.setParsedConditionJson(write(parsed));
		condition.setSubmittedBy(authentication.getName());
		conditions.save(condition);
		if (Boolean.TRUE.equals(parsed.get("blockWriteTools"))
				&& Set.of("WAITING_CONFIRMATION", "WAITING_APPROVAL", "PAUSED").contains(originalStatus)) {
			for (OperationAgentStepRun step : stepRuns.findByRunIdOrderByStepOrder(id)) {
				if (Set.of("WAITING_CONFIRMATION", "WAITING_APPROVAL").contains(step.getStatus())
						&& toolCatalog.require(step.getToolCode()).writeTool()) {
					step.setStatus("QUEUED");
					step.setMessage("附加条件禁止正式数据写入，等待执行器在检查点安全跳过");
					stepRuns.save(step);
				}
			}
		}
		run.setConditionVersion(nextVersion);
		boolean waitingForCheckpoint = Set.of("RUNNING", "PAUSE_REQUESTED").contains(originalStatus);
		if (!waitingForCheckpoint) {
			run.setStatus("PAUSED");
			run.setPauseRequested(false);
		}
		run.setNextAction(waitingForCheckpoint
				? "附加条件 V" + nextVersion + " 已接收，等待当前步骤完成后在安全检查点暂停"
				: Boolean.TRUE.equals(parsed.get("blockWriteTools"))
						? "附加条件 V" + nextVersion + " 已生效，继续后由执行器在检查点跳过写入步骤"
						: "附加条件 V" + nextVersion + " 已合并到后续步骤参数，请检查影响后继续执行");
		runs.save(run);
		events.append(id, "CONDITION_ADDED", "INFO", "已增加附加条件 V" + nextVersion,
				condition.getConditionText(), write(Map.of("impactAnalysis", condition.getImpactAnalysis())));
		audit.log(authentication.getName(), "OPERATION_AGENT_CONDITION_ADD",
				"runId=" + id + ", version=" + nextVersion);
		return runView(run);
	}

	/**
	 * Supplies trusted values for a step that stopped before execution. The run
	 * remains paused so the owner can review the merged input before resuming.
	 */
	@Transactional
	public RunView supplyStepInput(String id, StepInputCommand command, Authentication authentication) {
		if (command == null || command.stepOrder() == null || command.values() == null || command.values().isEmpty()) {
			throw new IllegalArgumentException("补充资料不能为空");
		}
		OperationAgentRun run = lockedOwnedRun(id, authentication);
		if (!"PAUSED".equals(run.getStatus())) {
			throw new IllegalStateException("只有等待补充资料的暂停实例可以提交资料");
		}
		OperationAgentStepRun step = stepRuns.findByRunIdAndStepOrder(id, command.stepOrder())
				.orElseThrow(() -> new IllegalArgumentException("执行步骤不存在"));
		if (!"WAITING_INPUT".equals(step.getStatus())) {
			throw new IllegalStateException("当前步骤没有等待补充资料");
		}
		Map<String, Object> overrides = readMap(step.getConfigOverridesJson());
		for (Map.Entry<String, Object> entry : command.values().entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().startsWith("__")) {
				throw new IllegalArgumentException("补充资料包含非法字段");
			}
			overrides.put(entry.getKey(), entry.getValue());
		}
		Map<String, Object> effective = stepSnapshotConfig(run, step.getStepOrder());
		effective.putAll(overrides);
		List<OperationAgentToolCatalog.InputRequirement> missing =
				toolCatalog.missingInputs(step.getToolCode(), effective);
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("仍缺少：" + missing.stream()
					.map(OperationAgentToolCatalog.InputRequirement::label)
					.collect(java.util.stream.Collectors.joining("、")));
		}
		step.setConfigOverridesJson(write(overrides));
		step.setStatus("QUEUED");
		step.setMessage("补充资料已校验，等待继续执行");
		stepRuns.save(step);
		run.setNextAction("资料已补充，请确认后继续执行“" + step.getStepName() + "”");
		runs.save(run);
		events.append(id, "INPUT_SUPPLIED", "INFO", "执行资料已补充",
				authentication.getName() + " 已补充“" + step.getStepName() + "”所需资料。",
				write(Map.of("stepOrder", step.getStepOrder(), "keys", overrides.keySet())));
		audit.log(authentication.getName(), "OPERATION_AGENT_STEP_INPUT",
				"runId=" + id + ", stepOrder=" + step.getStepOrder());
		return runView(run);
	}

	@Transactional
	public RunView cancel(String id, Authentication authentication) {
		OperationAgentRun run = lockedOwnedRun(id, authentication);
		AgentRunStatus current = AgentRunStatus.valueOf(run.getStatus());
		if (!AgentRunStateMachine.canTransition(current, AgentRunStatus.CANCELLED)) {
			throw new IllegalStateException("当前状态不能取消");
		}
		run.setStatus("CANCELLED");
		run.setPauseRequested(false);
		run.setCompletedAt(LocalDateTime.now());
		run.setNextAction("运行实例已取消");
		runs.save(run);
		events.append(id, "RUN_CANCELLED", "WARN", "执行已取消",
				authentication.getName() + " 取消了本次执行。", null);
		audit.log(authentication.getName(), "OPERATION_AGENT_RUN_CANCEL", "runId=" + id);
		return runView(run);
	}

	@Transactional
	public RunView retry(String id, Authentication authentication) {
		OperationAgentRun run = lockedOwnedRun(id, authentication);
		if (!"FAILED".equals(run.getStatus())) {
			throw new IllegalStateException("只有失败的运行实例可以重试");
		}
		if (run.getPlanId() == null || !plans.existsById(run.getPlanId())) {
			throw new IllegalStateException("执行计划已删除，历史运行只能查看，不能重试");
		}
		OperationAgentStepRun failedStep = stepRuns.findByRunIdOrderByStepOrder(id).stream()
				.filter(value -> "FAILED".equals(value.getStatus()))
				.findFirst()
				// 兼容旧版本在建立下一审批检查点时失败、只将运行实例标成
				// FAILED 而步骤仍为 QUEUED 的现场数据。
				.orElseGet(() -> stepRuns.findByRunIdOrderByStepOrder(id).stream()
						.filter(value -> !Set.of("SUCCEEDED", "SKIPPED").contains(value.getStatus()))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("没有可重试的失败步骤")));
		failedStep.setStatus("QUEUED");
		failedStep.setProgress(0);
		failedStep.setAttemptNo(0);
		failedStep.setApprovedBy(null);
		failedStep.setApprovedAt(null);
		failedStep.setDecisionAction(null);
		failedStep.setDecisionComment(null);
		failedStep.setEvidenceHash(null);
		failedStep.setStartedAt(null);
		failedStep.setCompletedAt(null);
		failedStep.setResultJson(null);
		failedStep.setMessage(failedStep.getCheckpointMode() == OperationAgentCheckpointMode.INITIATOR_CONFIRM
				? "失败步骤重试前需要发起人重新确认"
				: Boolean.TRUE.equals(failedStep.getApprovalRequired())
						? "失败步骤重试前需要重新审核" : "等待重新执行");
		stepRuns.save(failedStep);
		AgentRunStateMachine.assertTransition(AgentRunStatus.FAILED, AgentRunStatus.QUEUED);
		run.setStatus("QUEUED");
		run.setCurrentStepOrder(failedStep.getStepOrder());
		run.setCurrentStepName(failedStep.getStepName());
		run.setErrorMessage(null);
		run.setCompletedAt(null);
		run.setNextAction(failedStep.getCheckpointMode() == OperationAgentCheckpointMode.INITIATOR_CONFIRM
				? "等待重新生成候选结果并由发起人确认"
				: Boolean.TRUE.equals(failedStep.getApprovalRequired())
						? "请由非发起人重新审核失败步骤" : "等待执行器重新调用失败步骤");
		runs.save(run);
		events.append(id, "RUN_RETRIED", "INFO", "已请求重试失败步骤",
				authentication.getName() + " 请求从“" + failedStep.getStepName() + "”继续。", null);
		audit.log(authentication.getName(), "OPERATION_AGENT_RUN_RETRY",
				"runId=" + id + ", stepOrder=" + failedStep.getStepOrder());
		submitAfterCommit(id);
		return runView(run);
	}

	public List<OperationAgentToolCatalog.ToolDefinition> tools() {
		return toolCatalog.all();
	}

	private OperationAgentRun ownedRun(String id, Authentication authentication) {
		OperationAgentRun run = runs.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
		if (!authentication.getName().equals(run.getOwnerUsername()) && !hasReviewPermission(authentication)) {
			throw new IllegalStateException("无权查看该运行实例");
		}
		return run;
	}

	private boolean hasReviewPermission(Authentication authentication) {
		return authentication.getAuthorities().stream().anyMatch(value ->
				"education:operation-agent:review".equals(value.getAuthority())
						|| "ROLE_SUPER_ADMIN".equals(value.getAuthority()));
	}

	private OperationAgentRun lockedOwnedRun(String id, Authentication authentication) {
		OperationAgentRun run = runs.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
		if (!authentication.getName().equals(run.getOwnerUsername())) {
			throw new IllegalStateException("无权操作该运行实例");
		}
		return run;
	}

	private PlanView view(OperationAgentPlan plan) {
		return new PlanView(plan, planSteps.findByPlanIdOrderByStepOrder(plan.getId()), readList(plan.getAuthorizedToolsJson()));
	}

	private RunView runView(OperationAgentRun run) {
		OperationAgentPlan plan = run.getPlanId() == null
				? null
				: plans.findById(run.getPlanId()).orElse(null);
		return new RunView(run, plan == null ? "已删除计划" : plan.getPlanName());
	}

	private boolean candidatePromotionAvailable(OperationAgentRun run) {
		if (!"COMPLETED".equals(run.getStatus()) || run.getPlanId() == null) return false;
		for (OperationAgentPlanStep step : planSteps.findByPlanIdOrderByStepOrder(run.getPlanId())) {
			if (!toolCatalog.require(step.getToolCode()).writeTool()) continue;
			if (Boolean.TRUE.equals(stepSnapshotConfig(run, step.getStepOrder()).get("dryRun"))) return true;
		}
		return false;
	}

	private String snapshot(OperationAgentPlan plan, List<OperationAgentPlanStep> steps) {
		Map<String, Object> configs = new LinkedHashMap<>();
		for (OperationAgentPlanStep step : steps) {
			try {
				configs.put(String.valueOf(step.getStepOrder()),
						mapper.readValue(step.getToolConfigJson(), new TypeReference<Map<String, Object>>() {}));
			} catch (Exception exception) {
				throw new IllegalArgumentException("步骤“" + step.getStepName() + "”工具配置无效");
			}
		}
		return write(Map.of(
				"planId", plan.getId(),
				"planVersion", plan.getPlanVersion(),
				"objective", plan.getObjective(),
				"ruleText", plan.getRuleText(),
				"authorizedTools", readList(plan.getAuthorizedToolsJson()),
				"stepConfigs", configs));
	}

	private void validate(PlanCommand command) {
		if (command == null || command.planName() == null || command.planName().isBlank()) {
			throw new IllegalArgumentException("计划名称不能为空");
		}
		if (command.objective() == null || command.objective().isBlank()) {
			throw new IllegalArgumentException("计划目标不能为空");
		}
		if (command.objective().length() > 1000) {
			throw new IllegalArgumentException("计划目标摘要不能超过1000字，请将详细条件放在执行规则中");
		}
		if (command.ruleText() == null || command.ruleText().isBlank()) {
			throw new IllegalArgumentException("执行规则不能为空");
		}
		if (command.steps() == null || command.steps().isEmpty()) {
			throw new IllegalArgumentException("至少配置一个执行步骤");
		}
		if (command.authorizedTools() == null || command.authorizedTools().isEmpty()) {
			throw new IllegalArgumentException("至少授权一个业务工具");
		}
		if (command.periodStart() != null && command.periodEnd() != null
				&& command.periodEnd().isBefore(command.periodStart())) {
			throw new IllegalArgumentException("规划周期结束日期不能早于开始日期");
		}
		if ("CRON".equals(normalize(command.cycleType(), "MANUAL"))) {
			if (command.cronExpression() == null || command.cronExpression().isBlank()) {
				throw new IllegalArgumentException("周期执行计划必须填写 Cron 表达式");
			}
			try { CronExpression.parse(command.cronExpression().trim()); }
			catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Cron 表达式无效", exception); }
		}
		for (StepCommand step : command.steps()) {
			if (step.stepName() == null || step.stepName().isBlank()) {
				throw new IllegalArgumentException("步骤名称不能为空");
			}
			List<OperationAgentToolCatalog.InputRequirement> missing =
					toolCatalog.missingInputs(step.toolCode(), step.toolConfig());
			if (!missing.isEmpty()) {
				throw new IllegalArgumentException("步骤“" + step.stepName() + "”缺少执行资料："
						+ missing.stream().map(OperationAgentToolCatalog.InputRequirement::label)
								.collect(java.util.stream.Collectors.joining("、")));
			}
		}
		validateBusinessStageOrder(command.steps());
	}

	/**
	 * 人工编辑和 AI 草案保存都必须遵守开学业务依赖，避免把排课发布放在新生建档、
	 * 教师分工等前置步骤之前。期末工具不属于开学七阶段，不参与此顺序比较。
	 */
	private void validateBusinessStageOrder(List<StepCommand> steps) {
		int previousStage = 0;
		for (StepCommand step : steps) {
			OperationAgentToolCatalog.ToolDefinition definition = toolCatalog.require(step.toolCode());
			Integer stage = definition.businessStageOrder();
			if (stage == null) continue;
			if (stage < previousStage) {
				throw new IllegalArgumentException("执行步骤顺序不符合开学业务依赖：阶段"
						+ stage + "“" + definition.businessStageName() + "”不能排在阶段" + previousStage + "之后");
			}
			previousStage = stage;
		}
	}

	private String write(Object value) {
		try { return mapper.writeValueAsString(value); }
		catch (Exception exception) { throw new IllegalArgumentException("JSON 配置无效", exception); }
	}

	private Map<String, Object> readMap(String json) {
		try {
			if (json == null || json.isBlank()) return new LinkedHashMap<>();
			return new LinkedHashMap<>(mapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
		} catch (Exception exception) {
			throw new IllegalArgumentException("步骤补充资料配置无效", exception);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> stepSnapshotConfig(OperationAgentRun run, Integer stepOrder) {
		Map<String, Object> snapshot = readMap(run.getPlanSnapshotJson());
		Object rawConfigs = snapshot.get("stepConfigs");
		if (!(rawConfigs instanceof Map<?, ?> configs)) return new LinkedHashMap<>();
		Object rawConfig = configs.get(String.valueOf(stepOrder));
		if (!(rawConfig instanceof Map<?, ?> config)) return new LinkedHashMap<>();
		Map<String, Object> result = new LinkedHashMap<>();
		config.forEach((key, value) -> result.put(String.valueOf(key), value));
		return result;
	}

	private String impactAnalysis(Map<String, Object> parsed) {
		List<String> effects = new ArrayList<>();
		if (parsed.containsKey("admissionYear")) effects.add("后续招生工具入学年份=" + parsed.get("admissionYear"));
		if (parsed.containsKey("batchLimit")) effects.add("后续批处理上限=" + parsed.get("batchLimit"));
		if (parsed.containsKey("semesterCode")) effects.add("后续教务检查学期=" + parsed.get("semesterCode"));
		if (Boolean.TRUE.equals(parsed.get("blockWriteTools"))) effects.add("跳过所有尚未执行的高风险写入步骤");
		if (parsed.get("llmRationale") != null && !String.valueOf(parsed.get("llmRationale")).isBlank()) {
			effects.add("模型解析说明=" + parsed.get("llmRationale"));
		}
		String applied = effects.isEmpty() ? "未识别到可自动执行的结构化条件，条件原文仍已留痕，继续前需人工检查"
				: String.join("；", effects);
		return applied + "。已成功步骤及其正式业务数据不会被自动回滚。";
	}

	private void submitAfterCommit(String runId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			runner.submit(runId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				runner.submit(runId);
			}
		});
	}

	private List<String> readList(String value) {
		try { return mapper.readValue(value, new TypeReference<List<String>>() {}); }
		catch (Exception exception) { return List.of(); }
	}

	private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private LocalDateTime nextScheduledAt(
			String cycleType,
			String cronExpression,
			java.time.LocalDate periodStart,
			java.time.LocalDate periodEnd,
			LocalDateTime after) {
		if (!"CRON".equals(cycleType) || cronExpression == null || cronExpression.isBlank()) return null;
		LocalDateTime next = CronExpression.parse(cronExpression).next(after);
		if (next == null) return null;
		if (periodStart != null && next.toLocalDate().isBefore(periodStart)) {
			next = CronExpression.parse(cronExpression).next(periodStart.atStartOfDay().minusSeconds(1));
		}
		return next != null && periodEnd != null && next.toLocalDate().isAfter(periodEnd) ? null : next;
	}
	private String normalize(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
	}

	public record PlanCommand(
			String planName,
			String objective,
			String ruleText,
			String cycleType,
			String cronExpression,
			java.time.LocalDate periodStart,
			java.time.LocalDate periodEnd,
			String knowledgeBaseId,
			String visibility,
			Boolean enabled,
			List<String> authorizedTools,
			List<StepCommand> steps) {}
	public record StepCommand(
			String stepCode,
			String stepName,
			String description,
			String toolCode,
			Boolean approvalRequired,
			String successCriteria,
			Map<String, Object> toolConfig) {}
	public record ConditionCommand(String conditionText) {}
	public record ReviewCommand(String comment) {}
	public record CheckpointDecisionCommand(
			String action,
			Integer evidenceVersion,
			String evidenceHash,
			String comment) {}
	public record StepInputCommand(Integer stepOrder, Map<String, Object> values) {}
	public record PlanView(
			OperationAgentPlan plan,
			List<OperationAgentPlanStep> steps,
			List<String> authorizedTools) {}
	public record RunView(OperationAgentRun run, String planName) {}
	public record ConversationCommand(
			String request,
			List<String> contextMessages,
			String knowledgeBaseId,
			String semesterCode,
			Integer admissionYear,
			String managedFileId,
			List<String> managedFileIds,
			Boolean candidateOnly,
			Boolean confirmNonCurrent,
			Boolean createMissingTerm,
			String organizationId) {}
	public record ConversationResult(
			String status,
			OperationAgentLlmService.DraftResult draft,
			PlanView plan,
			RunView run,
			OperationAgentSemesterResolver.Resolution resolution) {}
	public record RunDetail(
			RunView overview,
			List<OperationAgentStepRun> steps,
			List<OperationAgentCondition> conditions,
			boolean candidatePromotionAvailable,
			List<String> eligibleReviewers,
			CheckpointView checkpoint) {}
	public record CheckpointView(
			OperationAgentCheckpointMode mode,
			String label,
			String reason,
			boolean ownerCanConfirm,
			boolean reviewerCanApprove,
			List<String> allowedActions,
			Integer evidenceVersion,
			String evidenceHash) {}
	public record PromotionResult(PlanView plan, RunView run) {}
}
