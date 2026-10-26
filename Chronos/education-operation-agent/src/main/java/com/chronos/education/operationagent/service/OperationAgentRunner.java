package com.chronos.education.operationagent.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.chronos.agent.AgentRunStateMachine;
import com.chronos.agent.AgentRunStatus;
import com.chronos.education.operationagent.dao.*;
import com.chronos.education.operationagent.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 在每个步骤边界重新读取运行状态，暂停请求只在安全检查点生效。 */
@Service
public class OperationAgentRunner {
	private final Executor executor;
	private final OperationAgentRunRepository runs;
	private final OperationAgentStepRunRepository stepRuns;
	private final OperationAgentPlanRepository plans;
	private final OperationAgentConditionRepository conditions;
	private final OperationAgentBusinessTools tools;
	private final OperationAgentToolCatalog toolCatalog;
	private final OperationAgentEventService events;
	private final OperationAgentNotificationService notifications;
	private final OperationAgentLlmService llm;
	private final ObjectMapper mapper;
	/**
	 * 同一运行实例在单节点内只能有一个执行线程。审批、重试和调度可能在很短
	 * 时间内重复提交同一个 runId；如果两个线程同时读取到 QUEUED，会重复调用
	 * 正式业务工具并触发 JPA 乐观锁冲突。
	 */
	private final Set<String> submittedRuns = ConcurrentHashMap.newKeySet();

	public OperationAgentRunner(
			@Qualifier("operationAgentExecutor") Executor executor,
			OperationAgentRunRepository runs,
			OperationAgentStepRunRepository stepRuns,
			OperationAgentPlanRepository plans,
			OperationAgentConditionRepository conditions,
			OperationAgentBusinessTools tools,
			OperationAgentToolCatalog toolCatalog,
			OperationAgentEventService events,
			OperationAgentNotificationService notifications,
			OperationAgentLlmService llm,
			ObjectMapper mapper) {
		this.executor = executor;
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.plans = plans;
		this.conditions = conditions;
		this.tools = tools;
		this.toolCatalog = toolCatalog;
		this.events = events;
		this.notifications = notifications;
		this.llm = llm;
		this.mapper = mapper;
	}

	public void submit(String runId) {
		if (!submittedRuns.add(runId)) return;
		executor.execute(() -> {
			try {
				execute(runId);
			} finally {
				submittedRuns.remove(runId);
			}
		});
	}

	/** 供审核界面按需刷新当前检查点的只读候选证据。 */
	public void refreshApprovalEvidence(String runId) {
		OperationAgentRun run = runs.findById(runId)
				.orElseThrow(() -> new IllegalArgumentException("运行实例不存在"));
		if (!"WAITING_APPROVAL".equals(run.getStatus())) {
			throw new IllegalStateException("当前没有等待确认的步骤");
		}
		OperationAgentStepRun step = stepRuns.findByRunIdAndStepOrder(runId, run.getCurrentStepOrder())
				.orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
		OperationAgentPlan plan = plans.findById(run.getPlanId())
				.orElseThrow(() -> new IllegalStateException("执行计划不存在"));
		try {
			refreshApprovalEvidence(run, plan, step, readSet(plan.getAuthorizedToolsJson()),
					effectiveConfig(run, step));
		} catch (RuntimeException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("刷新候选证据失败：" + exception.getMessage(), exception);
		}
	}

	private void execute(String runId) {
		OperationAgentRun run = runs.findById(runId).orElse(null);
		if (run == null || !"QUEUED".equals(run.getStatus())) return;
		try {
			move(run, "RUNNING");
			if (run.getStartedAt() == null) run.setStartedAt(LocalDateTime.now());
			run.setPausedAt(null);
			runs.save(run);
			events.append(runId, "RUN_STARTED", "INFO", "智能体开始执行",
					"执行器已载入计划快照，将在每个步骤完成后建立安全检查点。", null);

			OperationAgentPlan plan = run.getPlanId() == null
					? null
					: plans.findById(run.getPlanId()).orElse(null);
			if (plan == null) {
				throw new IllegalStateException("执行计划已删除，历史运行不能重新执行");
			}
			Set<String> authorized = readSet(plan.getAuthorizedToolsJson());
			for (OperationAgentStepRun step : stepRuns.findByRunIdOrderByStepOrder(runId)) {
				if ("SUCCEEDED".equals(step.getStatus()) || "SKIPPED".equals(step.getStatus())) continue;
				run = runs.findById(runId).orElseThrow();
				if ("CANCELLED".equals(run.getStatus())) return;
				if (Boolean.TRUE.equals(run.getPauseRequested())) {
					pauseAtCheckpoint(run, step);
					return;
				}
				OperationAgentToolCatalog.ToolDefinition definition = toolCatalog.require(step.getToolCode());
				Map<String, Object> config = effectiveConfig(run, step);
				if (definition.writeTool() && Boolean.TRUE.equals(config.get("blockWriteTools"))) {
					skipBlockedWriteStep(run, step);
					continue;
				}
				List<OperationAgentToolCatalog.InputRequirement> missing =
						toolCatalog.missingInputs(step.getToolCode(), config);
				if (!missing.isEmpty()) {
					waitForInput(run, step, missing);
					return;
				}
				boolean candidatePreview = Boolean.TRUE.equals(config.get("dryRun"));
				boolean humanRequired = definition.approvalRequired() && !candidatePreview;
				OperationAgentLlmService.StepDecision decision = new OperationAgentLlmService.StepDecision(
						humanRequired ? "REQUIRE_HUMAN" : "PROCEED",
						humanRequired
								? "正式写入工具命中平台预设审批规则。"
								: candidatePreview
										? "当前步骤只生成候选结果，可由受控工具直接执行。"
										: "当前工具和参数已通过平台确定性校验。",
						humanRequired ? "请审核后继续执行正式数据写入" : "调用受控业务工具");
				events.append(runId, "PLATFORM_STEP_DECISION",
						"REQUIRE_HUMAN".equals(decision.decision()) ? "WARN" : "INFO",
						"平台安全决策：" + step.getStepName(), decision.rationale(),
						writeEvidence(Map.of(
								"decision", decision.decision(),
								"nextAction", decision.nextAction() == null ? "" : decision.nextAction(),
								"toolCode", step.getToolCode())));
				// The user may request a pause while this iteration is preparing the step.
				// Re-read the run before changing step state so the execution thread remains
				// the only writer that applies the new condition at a safe checkpoint.
				run = runs.findById(runId).orElseThrow();
				if (Boolean.TRUE.equals(run.getPauseRequested())) {
					pauseAtCheckpoint(run, step);
					return;
				}
				if ("REQUIRE_HUMAN".equals(decision.decision()) && step.getApprovedAt() == null) {
					step.setApprovalRequired(true);
					step.setMessage(decision.nextAction() == null || decision.nextAction().isBlank()
							? "平台安全策略要求人工确认" : decision.nextAction());
				}
				if (Boolean.TRUE.equals(step.getApprovalRequired()) && step.getApprovedAt() == null) {
					refreshApprovalEvidence(run, plan, step, authorized, config);
					// Runner itself is intentionally non-transactional. Repository save() in
					// the preview call advances the optimistic-lock version, so continue with
					// the freshly persisted entity instead of the stale loop instance.
					step = stepRuns.findById(step.getId()).orElseThrow();
					step.setStatus("WAITING_APPROVAL");
					step.setMessage("此步骤将写入正式业务数据，需要人工确认");
					stepRuns.save(step);
					move(run, "WAITING_APPROVAL");
					run.setCurrentStepOrder(step.getStepOrder());
					run.setCurrentStepName(step.getStepName());
					run.setNextAction("请审核“" + step.getStepName() + "”，批准后继续执行");
					runs.save(run);
					events.append(runId, "APPROVAL_REQUIRED", "WARN", "等待人工确认",
							"步骤“" + step.getStepName() + "”风险等级较高，智能体已停止在写入前检查点。", null);
					notifications.approvalRequired(run, step);
					return;
				}
				executeStep(run, plan, step, authorized, config);
			}
			run = runs.findById(runId).orElseThrow();
			if ("CANCELLED".equals(run.getStatus())) return;
			move(run, "COMPLETED");
			run.setCurrentStepName(null);
			run.setNextAction("执行计划已完成，可查看各步骤证据或再次发起");
			run.setSummary("全部计划步骤已完成，执行结果与过程记录已留痕");
			run.setCompletedAt(LocalDateTime.now());
			runs.save(run);
			events.append(runId, "RUN_COMPLETED", "SUCCESS", "执行计划已完成",
					run.getSummary(), null);
			notifications.completed(run);
		} catch (Exception exception) {
			OperationAgentRun failed = runs.findById(runId).orElse(null);
			if (failed != null) {
				java.util.Optional<OperationAgentStepRun> stepToFail = stepRuns
						.findByRunIdOrderByStepOrder(runId).stream()
						.filter(value -> "RUNNING".equals(value.getStatus()))
						.findFirst();
				// 失败也可能发生在生成下一个人工检查点时，此时步骤尚未进入
				// RUNNING。仍要把首个未完成步骤标成可重试，避免运行实例卡死。
				if (stepToFail.isEmpty()) {
					stepToFail = stepRuns.findByRunIdOrderByStepOrder(runId).stream()
							.filter(value -> !Set.of("SUCCEEDED", "SKIPPED").contains(value.getStatus()))
							.findFirst();
				}
				stepToFail.ifPresent(value -> {
							value.setStatus("FAILED");
							value.setProgress(100);
							value.setMessage("执行失败：" + trim(exception.getMessage(), 800));
							value.setCompletedAt(LocalDateTime.now());
							try {
								value.setResultJson(mapper.writeValueAsString(Map.of(
										"error", trim(exception.getMessage(), 1000),
										"recovery", toolCatalog.require(value.getToolCode()).recoveryDescription())));
							} catch (Exception ignored) {
								value.setResultJson("{\"error\":\"执行失败\"}");
							}
							stepRuns.save(value);
						});
				failed.setStatus("FAILED");
				failed.setErrorMessage(trim(exception.getMessage(), 1000));
				failed.setCompletedAt(LocalDateTime.now());
				OperationAgentPlan failedPlan = failed.getPlanId() == null
						? null
						: plans.findById(failed.getPlanId()).orElse(null);
				OperationAgentStepRun failedStep = stepRuns.findByRunIdOrderByStepOrder(runId).stream()
						.filter(value -> "FAILED".equals(value.getStatus())).findFirst().orElse(null);
				String recovery = failedPlan == null || failedStep == null
						? "请检查失败原因，修正规则或数据后点击重试"
						: llm.analyzeFailure(failedPlan, failedStep, exception, failed.getOwnerUsername());
				failed.setNextAction(recovery);
				runs.save(failed);
				events.append(runId, "RUN_FAILED", "ERROR", "执行失败",
						failed.getErrorMessage() + "；DeepSeek 恢复建议：" + recovery, null);
				notifications.failed(failed);
			}
		}
	}

	/**
	 * 审批人必须看到基于当前业务状态计算出的候选结果。正式链路会在前一步
	 * 写入后才到达下一检查点，因此不能继续展示创建正式运行时复制的旧候选证据。
	 * 这里强制改为 dry-run，只刷新证据而不写入任何正式数据。
	 */
	private void refreshApprovalEvidence(
			OperationAgentRun run,
			OperationAgentPlan plan,
			OperationAgentStepRun step,
			Set<String> authorized,
			Map<String, Object> config) throws Exception {
		if (!authorized.contains(step.getToolCode())) {
			throw new IllegalStateException("计划快照未授权工具：" + step.getToolCode());
		}
		Map<String, Object> previewConfig = new java.util.LinkedHashMap<>(config);
		previewConfig.put("dryRun", true);
		Map<String, Object> preview = executeToolAs(
				run.getOwnerUsername(), step.getToolCode(), previewConfig,
				plan.getKnowledgeBaseId(), plan.getObjective());
		step.setResultJson(mapper.writeValueAsString(preview));
		step.setMessage("已按当前业务数据生成最新候选结果，等待人工确认");
		stepRuns.save(step);
		events.append(run.getId(), "APPROVAL_EVIDENCE_REFRESHED", "INFO",
				"已刷新候选证据：" + step.getStepName(),
				"审批检查点已基于前序步骤的最新结果重新执行只读预演。", step.getResultJson());
	}

	private void executeStep(
			OperationAgentRun run,
			OperationAgentPlan plan,
			OperationAgentStepRun step,
			Set<String> authorized,
			Map<String, Object> config) throws Exception {
		if (!authorized.contains(step.getToolCode())) {
			throw new IllegalStateException("计划快照未授权工具：" + step.getToolCode());
		}
		OperationAgentToolCatalog.ToolDefinition definition = toolCatalog.require(step.getToolCode());
		step.setStatus("RUNNING");
		step.setProgress(10);
		step.setStartedAt(LocalDateTime.now());
		step.setCompletedAt(null);
		step.setMessage("正在调用受控业务工具");
		step = stepRuns.save(step);
		run.setCurrentStepOrder(step.getStepOrder());
		run.setCurrentStepName(step.getStepName());
		run.setNextAction("完成当前步骤后进入下一个安全检查点");
		runs.save(run);
		events.append(run.getId(), "STEP_STARTED", "INFO", "开始：" + step.getStepName(),
				"调用工具 " + step.getToolCode(), null);

		String actor = definition.approvalActorExecutes() && step.getApprovedBy() != null
				? step.getApprovedBy() : run.getOwnerUsername();
		int maxAttempts = Math.max(1, definition.maxAttempts());
		Exception lastFailure = null;
		while (step.getAttemptNo() < maxAttempts) {
			step.setAttemptNo(step.getAttemptNo() + 1);
			step.setMessage("正在调用受控业务工具，第 " + step.getAttemptNo() + " 次尝试");
			step = stepRuns.save(step);
			try {
				Map<String, Object> result = executeToolAs(
						actor, step.getToolCode(), config, plan.getKnowledgeBaseId(), plan.getObjective());
				step.setResultJson(mapper.writeValueAsString(result));
				step.setStatus("SUCCEEDED");
				step.setProgress(100);
				step.setMessage("步骤执行成功，证据已保存");
				step.setCompletedAt(LocalDateTime.now());
				stepRuns.save(step);
				events.append(run.getId(), "STEP_SUCCEEDED", "SUCCESS", "完成：" + step.getStepName(),
						"业务工具返回结果已作为执行证据保存。", step.getResultJson());
				return;
			} catch (Exception exception) {
				lastFailure = exception;
				if (step.getAttemptNo() < maxAttempts) {
					step.setMessage("本次调用失败，执行器将自动重试：" + trim(exception.getMessage(), 600));
					step = stepRuns.save(step);
					events.append(run.getId(), "STEP_RETRY", "WARN", "自动重试：" + step.getStepName(),
							"第 " + step.getAttemptNo() + " 次调用失败；恢复策略：" + definition.recoveryDescription(), null);
				}
			}
		}
		throw lastFailure == null ? new IllegalStateException("业务工具执行失败") : lastFailure;
	}

	private Map<String, Object> effectiveConfig(
			OperationAgentRun run,
			OperationAgentStepRun step) throws Exception {
		Map<String, Object> snapshot = mapper.readValue(run.getPlanSnapshotJson(), new TypeReference<>() {});
		@SuppressWarnings("unchecked")
		Map<String, Object> configs = (Map<String, Object>) snapshot.getOrDefault("stepConfigs", Map.of());
		@SuppressWarnings("unchecked")
		Map<String, Object> config = (Map<String, Object>) configs.getOrDefault(
				String.valueOf(step.getStepOrder()), Map.of());
		config = new java.util.LinkedHashMap<>(config);
		if (step.getConfigOverridesJson() != null && !step.getConfigOverridesJson().isBlank()) {
			Map<String, Object> overrides = mapper.readValue(
					step.getConfigOverridesJson(), new TypeReference<Map<String, Object>>() {});
			config.putAll(overrides);
		}
		// Internal execution context keeps downstream scheduling steps bound to
		// candidates created by this run instead of accidentally selecting an older plan.
		config.put("__runId", run.getId());
		config.put("__runStartedAt", run.getStartedAt().toString());
		for (OperationAgentCondition condition : conditions.findByRunIdOrderByConditionVersion(run.getId())) {
			Map<String, Object> parsed = mapper.readValue(
					condition.getParsedConditionJson(), new TypeReference<Map<String, Object>>() {});
			for (String key : java.util.List.of("admissionYear", "batchLimit", "semesterCode", "blockWriteTools")) {
				if (parsed.containsKey(key)) config.put(key, parsed.get(key));
			}
		}
		return config;
	}

	private void skipBlockedWriteStep(OperationAgentRun run, OperationAgentStepRun step) {
		step.setStatus("SKIPPED");
		step.setProgress(100);
		step.setMessage("附加条件禁止正式数据写入，本步骤已跳过");
		step.setCompletedAt(LocalDateTime.now());
		stepRuns.save(step);
		events.append(run.getId(), "STEP_SKIPPED", "WARN", "已跳过：" + step.getStepName(),
				"用户附加条件禁止正式数据写入，执行器未调用业务工具。", null);
	}

	private void waitForInput(
			OperationAgentRun run,
			OperationAgentStepRun step,
			List<OperationAgentToolCatalog.InputRequirement> missing) {
		String labels = missing.stream().map(OperationAgentToolCatalog.InputRequirement::label)
				.collect(java.util.stream.Collectors.joining("、"));
		step.setStatus("WAITING_INPUT");
		step.setMessage("需要补充：" + labels);
		stepRuns.save(step);
		move(run, "PAUSE_REQUESTED");
		move(run, "PAUSED");
		run.setPausedAt(LocalDateTime.now());
		run.setCurrentStepOrder(step.getStepOrder());
		run.setCurrentStepName(step.getStepName());
		run.setNextAction("请补充“" + step.getStepName() + "”所需资料：" + labels);
		runs.save(run);
		events.append(run.getId(), "INPUT_REQUIRED", "WARN", "等待补充执行资料",
				run.getNextAction(), writeEvidence(missing));
	}

	private void pauseAtCheckpoint(OperationAgentRun run, OperationAgentStepRun nextStep) {
		if ("RUNNING".equals(run.getStatus())) move(run, "PAUSE_REQUESTED");
		move(run, "PAUSED");
		run.setPauseRequested(false);
		run.setPausedAt(LocalDateTime.now());
		run.setCurrentStepOrder(nextStep.getStepOrder());
		run.setCurrentStepName(nextStep.getStepName());
		run.setNextAction("可补充附加条件并评估影响，或直接继续执行");
		runs.save(run);
		events.append(run.getId(), "RUN_PAUSED", "WARN", "已在安全检查点暂停",
				"已完成的数据保持不变，尚未开始的步骤不会执行。", null);
	}

	private void move(OperationAgentRun run, String target) {
		AgentRunStatus from = AgentRunStatus.valueOf(run.getStatus());
		AgentRunStatus to = AgentRunStatus.valueOf(target);
		AgentRunStateMachine.assertTransition(from, to);
		run.setStatus(target);
	}

	private Set<String> readSet(String json) {
		try {
			return new LinkedHashSet<>(mapper.readValue(json, new TypeReference<java.util.List<String>>() {}));
		} catch (Exception exception) {
			throw new IllegalStateException("计划授权工具配置无效", exception);
		}
	}

	private String trim(String value, int max) {
		String text = value == null || value.isBlank() ? "未知执行错误" : value;
		return text.length() <= max ? text : text.substring(0, max);
	}

	private String writeEvidence(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception ignored) {
			return null;
		}
	}

	private Map<String, Object> executeToolAs(
			String actor,
			String toolCode,
			Map<String, Object> config,
			String knowledgeBaseId,
			String objective) {
		SecurityContext previous = SecurityContextHolder.getContext();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(actor, "", java.util.List.of()));
		SecurityContextHolder.setContext(context);
		try {
			return tools.execute(toolCode, config, knowledgeBaseId, objective, actor);
		} finally {
			SecurityContextHolder.setContext(previous);
		}
	}
}
