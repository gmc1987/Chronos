package com.chronos.education.scheduling.service;

import com.chronos.agent.AgentRunStatus;
import com.chronos.agent.AgentRunBudget;
import com.chronos.agent.AgentToolExecutor;
import com.chronos.agent.ToolContext;
import com.chronos.agent.ToolResult;
import com.chronos.education.scheduling.dao.AgentRunRepository;
import com.chronos.education.scheduling.dao.AgentStepRepository;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.AgentStep;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import com.chronos.education.scheduling.model.SchedulingCandidateExplanation;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.SchedulingAiConfirmRequest;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiOfferingConstraint;
import com.chronos.education.scheduling.model.SchedulingAiReplyRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Orchestrates the persisted state machine without exposing apply/publish tools. */
@Service
public class SchedulingAiRunService {
	private static final int EXPIRY_HOURS = 24;
	private static final Pattern NUMBERED_REPLY = Pattern.compile("([1-9]\\d*)[：:]\\s*(.+)");

	private final AgentRunRepository runs;
	private final AgentStepRepository steps;
	private final SchedulingAiRequirementParser parser;
	private final ScheduleGenerationJobService jobs;
	private final AutoSchedulingService autoScheduling;
	private final EducationDataScopeService dataScopes;
	private final ObjectMapper json;
	private final AgentToolExecutor tools;
	private final SchedulingAgentCandidateService candidateService;
	private final SchedulingAgentExplanationService explanations;
	private final TransactionTemplate transactions;
	private final ConcurrentHashMap<String, AgentRunBudget> budgets = new ConcurrentHashMap<>();

	public SchedulingAiRunService(
			AgentRunRepository runs,
			AgentStepRepository steps,
			SchedulingAiRequirementParser parser,
			ScheduleGenerationJobService jobs,
			AutoSchedulingService autoScheduling,
			EducationDataScopeService dataScopes,
			ObjectMapper json,
			AgentToolExecutor tools,
			SchedulingAgentCandidateService candidateService,
			SchedulingAgentExplanationService explanations,
			TransactionTemplate transactions) {
		this.runs = runs;
		this.steps = steps;
		this.parser = parser;
		this.jobs = jobs;
		this.autoScheduling = autoScheduling;
		this.dataScopes = dataScopes;
		this.json = json;
		this.tools = tools;
		this.candidateService = candidateService;
		this.explanations = explanations;
		this.transactions = transactions;
	}

	public SchedulingAiRunView create(SchedulingAiRunRequest request, String actor) {
		dataScopes.assertFullAccess(dataScopes.resolve(actor));
		String requestHash = parser.requestHash(request);
		var existing = runs.findByOwnerUsernameAndClientRequestId(
				actor, request.clientRequestId());
		if (existing.isPresent()) {
			AgentRun run = existing.get();
			if (!requestHash.equals(run.getRequestHash())) {
				throw new IllegalStateException("clientRequestId 已用于不同请求");
			}
			return view(synchronize(run));
		}
		SchedulingAiRequirementParser.ParsedRequirement parsed =
				parser.parse(request, actor);
		try {
			return transactions.execute(status -> {
				var raced = runs.findByOwnerUsernameAndClientRequestId(actor, request.clientRequestId());
				if (raced.isPresent()) {
					AgentRun previous = raced.get();
					if (!requestHash.equals(previous.getRequestHash())) {
						throw new IllegalStateException("clientRequestId 已用于不同请求");
					}
					return view(previous);
				}
				AgentRun run = new AgentRun();
				run.setClientRequestId(request.clientRequestId());
				run.setOwnerUsername(actor);
				run.setSemesterCode(request.semesterCode());
				run.setRequestHash(requestHash);
				run.setParsedPlanJson(write(parsed.plan()));
				run.setStatus(parsed.plan().readyForConfirmation()
						? "READY_FOR_CONFIRMATION"
						: "NEEDS_CLARIFICATION");
				run.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
				run = runs.save(run);
				var metadata = invoke(
						run, actor, SchedulingAgentCapabilities.REQUIREMENTS,
						SchedulingAgentCapabilities.CONTEXT, "term",
						AgentRunStatus.valueOf(run.getStatus()),
						new SchedulingAgentCapabilities.ContextInput(
								"TERM", request.semesterCode(), request.semesterCode(), 0, 10));
				var catalog = (SchedulingAgentCatalogService.Page) metadata;
				if (catalog.items().stream().noneMatch(item -> request.semesterCode().equals(item.code()))) {
					throw new IllegalStateException("目标学期未在授权基础数据中");
				}
				recordStep(run, "requirements.parse", "SCHEDULE_REQUIREMENTS_V1", "OK");
				return view(run);
			});
		} catch (DataIntegrityViolationException exception) {
			AgentRun concurrent = runs.findByOwnerUsernameAndClientRequestId(
					actor, request.clientRequestId()).orElseThrow(() -> exception);
			if (!requestHash.equals(concurrent.getRequestHash())) {
				throw new IllegalStateException("clientRequestId 已用于不同请求", exception);
			}
			return view(synchronize(concurrent));
		}
	}

	@Transactional
	public SchedulingAiRunView get(String id, String actor, boolean operations) {
		AgentRun run = requireVisible(id, actor, operations);
		return view(synchronize(run));
	}

	public SchedulingAiRunView reply(
			String id,
			SchedulingAiReplyRequest request,
			String actor) {
		AgentRun current = requireVisible(id, actor, false);
		requireVersion(current, request.expectedPlanVersion());
		requireState(current, "NEEDS_CLARIFICATION");
		dataScopes.assertFullAccess(dataScopes.resolve(actor));
		SchedulingAiPlan previous = readPlan(current.getParsedPlanJson());
		if (!previous.unsupported().isEmpty()) {
			throw new IllegalStateException("原需求包含不支持的规则，请修改原需求并新建 Run");
		}
		if (previous.unresolvedClauses().isEmpty()) {
			throw new IllegalStateException("此处的学期、模式或旧计划歧义需修改请求并新建 Run");
		}
		if (previous.clarifications().stream().anyMatch(item -> item.startsWith("自然语言中"))) {
			throw new IllegalStateException("学期或模式冲突需修改原需求并新建 Run");
		}
		Map<Integer, String> replacements = replyReplacements(
				request.answer(), previous.unresolvedClauses().size());
		SchedulingAiRunRequest reparsed = new SchedulingAiRunRequest(
				current.getClientRequestId() + "-reply-" + current.getPlanVersion(),
				current.getSemesterCode(),
				previous.mode(),
				previous.selectedOfferingIds(),
				previous.candidateCount(),
				String.join("；", replacements.values()));
		SchedulingAiPlan updated = parser.parse(reparsed, actor).plan();
		Set<String> parsedSources = updated.constraints().stream()
				.map(com.chronos.education.scheduling.model.SchedulingAiConstraint::sourceText)
				.collect(java.util.stream.Collectors.toSet());
		updated.offeringConstraints().stream().map(SchedulingAiOfferingConstraint::sourceText)
				.forEach(parsedSources::add);
		updated.weekRules().stream()
				.map(com.chronos.education.scheduling.model.SchedulingAiWeekRule::sourceText)
				.forEach(parsedSources::add);
		updated.lockedEntries().stream()
				.map(com.chronos.education.scheduling.model.SchedulingAiLockedEntry::sourceText)
				.forEach(parsedSources::add);
		updated.softPriorities().stream()
				.map(com.chronos.education.scheduling.model.SchedulingAiSoftPriority::sourceText)
				.forEach(parsedSources::add);
		updated.slotRules().stream()
				.map(com.chronos.education.scheduling.model.SchedulingAiSlotRule::sourceText)
				.forEach(parsedSources::add);
		if (!updated.readyForConfirmation()
				|| parsedSources.size() != replacements.size()
				|| !parsedSources.containsAll(replacements.values())) {
			throw new IllegalStateException("每条澄清回复须完整且可解析为受支持的排课规则");
		}
		List<com.chronos.education.scheduling.model.SchedulingAiConstraint> combined =
				new java.util.ArrayList<>(previous.constraints());
		for (var constraint : updated.constraints()) {
			if (combined.stream().anyMatch(existing ->
					existing.teacherId().equals(constraint.teacherId())
							&& existing.dayOfWeek().equals(constraint.dayOfWeek())
							&& existing.periodNo().equals(constraint.periodNo())
							&& !existing.strength().equals(constraint.strength()))) {
				throw new IllegalStateException("补充需求与已解析教师时段规则冲突，请重新创建请求");
			}
			if (combined.stream().noneMatch(existing ->
					existing.teacherId().equals(constraint.teacherId())
							&& existing.dayOfWeek().equals(constraint.dayOfWeek())
							&& existing.periodNo().equals(constraint.periodNo())
							&& existing.strength().equals(constraint.strength()))) {
				combined.add(constraint);
			}
		}
		List<String> remainingUnresolved = new ArrayList<>();
		for (int index = 0; index < previous.unresolvedClauses().size(); index++) {
			if (!replacements.containsKey(index + 1)) {
				remainingUnresolved.add(previous.unresolvedClauses().get(index));
			}
		}
		List<String> remainingClarifications = remainingUnresolved.isEmpty()
				? List.of()
				: List.of("仍有 " + remainingUnresolved.size() + " 条原需求待澄清，请按编号完整重述");
		List<SchedulingAiOfferingConstraint> combinedOfferings =
				new ArrayList<>(previous.offeringConstraints());
		for (var offering : updated.offeringConstraints()) {
			if (combinedOfferings.stream().noneMatch(existing ->
					existing.offeringId().equals(offering.offeringId()))) {
				combinedOfferings.add(offering);
			}
		}
		List<com.chronos.education.scheduling.model.SchedulingAiWeekRule> combinedWeekRules =
				new ArrayList<>(previous.weekRules());
		for (var weekRule : updated.weekRules()) {
			var existing = combinedWeekRules.stream()
					.filter(item -> item.offeringId().equals(weekRule.offeringId()))
					.findFirst();
			if (existing.isPresent() && (!existing.get().weekPattern().equals(weekRule.weekPattern())
					|| existing.get().startWeek() != weekRule.startWeek()
					|| existing.get().endWeek() != weekRule.endWeek())) {
				throw new IllegalStateException("补充需求与已解析教学周规则冲突，请重新创建请求");
			}
			if (existing.isEmpty()) {
				combinedWeekRules.add(weekRule);
			}
		}
		List<com.chronos.education.scheduling.model.SchedulingAiLockedEntry> combinedLocks =
				new ArrayList<>(previous.lockedEntries());
		for (var locked : updated.lockedEntries()) {
			if (combinedLocks.stream().anyMatch(existing ->
					existing.entryId().equals(locked.entryId())
							&& !existing.offeringId().equals(locked.offeringId()))) {
				throw new IllegalStateException("补充需求与已解析临时保留课表项冲突");
			}
			if (combinedLocks.stream().noneMatch(existing ->
					existing.entryId().equals(locked.entryId()))) {
				combinedLocks.add(locked);
			}
		}
		List<com.chronos.education.scheduling.model.SchedulingAiSoftPriority> combinedPriorities =
				new ArrayList<>(previous.softPriorities());
		for (var priority : updated.softPriorities()) {
			if (combinedPriorities.stream().noneMatch(existing ->
					existing.teacherId().equals(priority.teacherId())
							&& existing.kind().equals(priority.kind()))) {
				combinedPriorities.add(priority);
			}
		}
		List<com.chronos.education.scheduling.model.SchedulingAiSlotRule> combinedSlotRules =
				new ArrayList<>(previous.slotRules());
		for (var rule : updated.slotRules()) {
			if (combinedSlotRules.stream().anyMatch(existing ->
					existing.targetType().equals(rule.targetType())
							&& existing.targetId().equals(rule.targetId())
							&& existing.dayOfWeek() == rule.dayOfWeek()
							&& existing.periodNo() == rule.periodNo()
							&& !existing.sourceText().equals(rule.sourceText()))) {
				throw new IllegalStateException("补充需求与已解析组合时段规则重复，请重新创建请求");
			}
			if (!combinedSlotRules.contains(rule)) combinedSlotRules.add(rule);
		}
		if (combinedSlotRules.size() > 200) {
			throw new IllegalStateException("组合规则超出本轮排课数量上限");
		}
		SchedulingAiPlan plan = new SchedulingAiPlan(updated.schemaVersion(),
				updated.skillCode(), updated.semesterCode(), updated.mode(),
				updated.selectedOfferingIds(), updated.candidateCount(), combined,
				remainingClarifications, List.of(), remainingUnresolved, combinedOfferings,
				combinedWeekRules, combinedLocks, combinedPriorities, combinedSlotRules);
		return transactions.execute(status -> {
			AgentRun locked = lockedOwner(id, actor);
			requireVersion(locked, request.expectedPlanVersion());
			requireState(locked, "NEEDS_CLARIFICATION");
			locked.setPlanVersion(locked.getPlanVersion() + 1);
			locked.setParsedPlanJson(write(plan));
			locked.setStatus(plan.readyForConfirmation()
					? "READY_FOR_CONFIRMATION"
					: "NEEDS_CLARIFICATION");
			locked.setErrorCode(null);
			locked.setErrorMessage(null);
			recordStep(locked, "requirements.reply." + locked.getPlanVersion(),
					"SCHEDULE_REQUIREMENTS_V1", "OK");
			return view(runs.save(locked));
		});
	}

	private Map<Integer, String> replyReplacements(String answer, int unresolvedCount) {
		Map<Integer, String> replacements = new TreeMap<>();
		String[] lines = answer.split("\\R");
		if (unresolvedCount == 1 && lines.length == 1
				&& !NUMBERED_REPLY.matcher(answer).matches()) {
			replacements.put(1, answer.strip());
		} else {
			for (String line : lines) {
				Matcher matcher = NUMBERED_REPLY.matcher(line.strip());
				if (!matcher.matches()) {
					throw new IllegalArgumentException("多条待澄清规则请逐行用“编号：完整规则”回复");
				}
				int number = Integer.parseInt(matcher.group(1));
				if (number > unresolvedCount || replacements.putIfAbsent(
						number, matcher.group(2).strip()) != null) {
					throw new IllegalArgumentException("澄清规则编号重复或不存在");
				}
			}
		}
		if (replacements.isEmpty() || new HashSet<>(replacements.values()).size() != replacements.size()
				|| replacements.values().stream().anyMatch(value ->
						value.isBlank() || value.matches(".*[；;。，,\\n].*"))) {
			throw new IllegalArgumentException("每个编号只能替换为一条完整且不同的规则");
		}
		return replacements;
	}

	@Transactional
	public SchedulingAiRunView confirm(
			String id,
			SchedulingAiConfirmRequest request,
			String actor) {
		AgentRun current = lockedOwner(id, actor);
		requireVersion(current, request.expectedPlanVersion());
		requireState(current, "READY_FOR_CONFIRMATION");
		SchedulingAiPlan plan = readPlan(current.getParsedPlanJson());
		if (!plan.readyForConfirmation()) {
			throw new IllegalStateException("结构化需求仍包含待澄清项");
		}
		invoke(current, actor, SchedulingAgentCapabilities.CONSTRAINTS,
				SchedulingAgentCapabilities.CONSTRAINTS_VALIDATE,
				"constraints.validate." + current.getPlanVersion(),
				AgentRunStatus.READY_FOR_CONFIRMATION,
				new SchedulingAgentCapabilities.GenerationInput(current.getPlanVersion()));
		current.setConfirmedPlanJson(current.getParsedPlanJson());
		current.setStatus("CONFIRMED");
		recordStep(current, "requirements.confirm." + current.getPlanVersion(),
				"SCHEDULE_CONSTRAINTS_V1", "OK");
		return view(runs.save(current));
	}

	@Transactional
	public SchedulingAiRunView generate(String id, String actor) {
		AgentRun current = lockedOwner(id, actor);
		dataScopes.assertFullAccess(dataScopes.resolve(actor));
		if (current.getRelatedJobId() != null
				&& List.of("QUEUED", "RUNNING", "CANDIDATES_READY").contains(current.getStatus())) {
			return view(synchronize(current));
		}
		requireState(current, "CONFIRMED");
		invoke(current, actor, SchedulingAgentCapabilities.GENERATION,
				SchedulingAgentCapabilities.GENERATION_VALIDATE,
				"generation.validate." + current.getPlanVersion(),
				AgentRunStatus.CONFIRMED,
				new SchedulingAgentCapabilities.GenerationInput(current.getPlanVersion()));
		String jobId = (String) invoke(
				current, actor, SchedulingAgentCapabilities.GENERATION,
				SchedulingAgentCapabilities.GENERATE,
				"generation.submit." + current.getPlanVersion(),
				AgentRunStatus.CONFIRMED,
				new SchedulingAgentCapabilities.GenerationInput(current.getPlanVersion()));
		current.setRelatedJobId(jobId);
		current.setStatus("QUEUED");
		recordStep(current, "generation.submit." + current.getPlanVersion(),
				"SCHEDULE_GENERATE_V1", "OK");
		return view(runs.save(current));
	}

	private Object invoke(AgentRun run, String actor, String skill, String tool,
			String key, AgentRunStatus status, Object input) {
		ToolContext context = new ToolContext(run.getId(), actor, run.getSchoolId(),
				java.util.Map.of(), key, Instant.now().plusSeconds(10));
		String budgetKey = run.getId() + ":" + skill;
		AgentRunBudget budget = budgets.computeIfAbsent(budgetKey,
				ignored -> tools.newBudget(context, skill));
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int completionStatus) {
					budgets.remove(budgetKey, budget);
				}
			});
		}
		ToolResult<?> result = tools.invoke(skill, tool, 1, context, budget, status, input);
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			budgets.remove(budgetKey, budget);
		}
		if (!result.succeeded()) {
			throw new IllegalStateException("排课工具执行失败：" + result.resultCode() + " " + result.summary());
		}
		if (!List.of(SchedulingAgentCapabilities.STATUS, SchedulingAgentCapabilities.COMPARE,
				SchedulingAgentCapabilities.PREVIEW).contains(tool)
				&& steps.findByRunIdAndStepKey(run.getId(), key).isEmpty()) {
			AgentStep trace = new AgentStep();
			trace.setRunId(run.getId());
			trace.setStepNo(steps.findByRunIdOrderByStepNo(run.getId()).size() + 1);
			trace.setPlanVersion(run.getPlanVersion());
			trace.setStepKey(key);
			trace.setSkillCode(skill);
			trace.setToolCode(tool);
			trace.setState("SUCCEEDED");
			trace.setResultCode(result.resultCode());
			trace.setInputDigest(digest(tool + ":" + key));
			trace.setOutputSummaryJson(write(java.util.Map.of("summary", result.summary())));
			trace.setStartedAt(LocalDateTime.now());
			trace.setFinishedAt(LocalDateTime.now());
			trace.setDurationMs(0L);
			steps.save(trace);
		}
		return result.data();
	}

	@Transactional
	public List<ScheduleCandidateView> candidates(
			String id,
			String actor,
			boolean operations) {
		AgentRun run = synchronize(requireVisible(id, actor, operations));
		if (run.getRelatedJobId() == null) {
			return List.of();
		}
		ScheduleGenerationJob job = jobs.require(run.getRelatedJobId());
		if (!run.getId().equals(job.getAgentRunId())
				|| !run.getSemesterCode().equals(job.getSemesterCode())) {
			throw new IllegalStateException("排课任务与当前 AI Run 不匹配");
		}
		Set<String> ids = readIds(job.getResultCandidateIds());
		return autoScheduling.list(run.getSemesterCode()).stream()
				.filter(candidate -> ids.contains(candidate.id()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ScheduleCandidateView> compare(String id, String actor, List<String> candidateIds) {
		AgentRun run = requireVisible(id, actor, false);
		return candidateService.compare(run, actor, candidateIds);
	}

	@Transactional(readOnly = true)
	public ScheduleDiffView preview(String id, String actor, String candidateId) {
		AgentRun run = requireVisible(id, actor, false);
		return candidateService.preview(run, actor, candidateId);
	}

	public SchedulingCandidateExplanation explain(String id, String actor, String candidateId) {
		AgentRun run = requireVisible(id, actor, false);
		return explanations.explain(run, actor, candidateId);
	}

	@Transactional
	public SchedulingAiRunView cancel(String id, String actor, boolean operations) {
		AgentRun current = requireVisible(id, actor, operations);
		current = runs.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("AI Run 不存在"));
		if (!operations && !actor.equals(current.getOwnerUsername())) {
			throw new org.springframework.security.access.AccessDeniedException("无权操作该 AI Run");
		}
		if (current.getRelatedJobId() != null
				&& List.of("QUEUED", "RUNNING").contains(jobs.require(current.getRelatedJobId()).getStatus())) {
			jobs.cancel(current.getRelatedJobId(), actor);
		} else if (List.of("CANDIDATES_READY", "FAILED", "CANCELLED", "EXPIRED")
				.contains(current.getStatus())) {
			throw new IllegalStateException("当前 Run 状态不能取消");
		}
		current.setStatus("CANCELLED");
		current.setErrorCode("CANCELLED");
		current.setErrorMessage("已取消");
		return view(runs.save(current));
	}

	private AgentRun synchronize(AgentRun run) {
		if (run.getRelatedJobId() == null && run.getExpiresAt() != null
				&& run.getExpiresAt().isBefore(LocalDateTime.now())
				&& !List.of("CANDIDATES_READY", "FAILED", "CANCELLED", "EXPIRED")
						.contains(run.getStatus())) {
			run.setStatus("EXPIRED");
			return runs.save(run);
		}
		if (run.getRelatedJobId() == null) {
			return run;
		}
		ScheduleGenerationJob job = jobs.require(run.getRelatedJobId());
		if (!run.getId().equals(job.getAgentRunId())
				|| !run.getSemesterCode().equals(job.getSemesterCode())) {
			throw new IllegalStateException("排课任务与当前 AI Run 不匹配");
		}
		switch (job.getStatus()) {
			case "RUNNING" -> run.setStatus("RUNNING");
			case "SUCCEEDED" -> run.setStatus("CANDIDATES_READY");
			case "FAILED" -> {
				run.setStatus("FAILED");
				run.setErrorCode("GENERATION_FAILED");
				run.setErrorMessage(job.getErrorMessage());
			}
			case "CANCELLED" -> run.setStatus("CANCELLED");
			default -> {
				// QUEUED remains QUEUED.
			}
		}
		return runs.save(run);
	}

	private AgentRun requireVisible(String id, String actor, boolean operations) {
		AgentRun run = runs.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("AI Run 不存在"));
		if (!operations && !actor.equals(run.getOwnerUsername())) {
			throw new org.springframework.security.access.AccessDeniedException("无权访问该 AI Run");
		}
		return run;
	}

	private AgentRun lockedOwner(String id, String actor) {
		AgentRun run = runs.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("AI Run 不存在"));
		if (!actor.equals(run.getOwnerUsername())) {
			throw new org.springframework.security.access.AccessDeniedException("无权操作该 AI Run");
		}
		if (run.getExpiresAt() != null && run.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new IllegalStateException("AI Run 已过期");
		}
		return run;
	}

	private void requireVersion(AgentRun run, Integer expected) {
		if (!expected.equals(run.getPlanVersion())) {
			throw new IllegalStateException("计划版本已变化，请刷新后重试");
		}
	}

	private void requireState(AgentRun run, String expected) {
		if (!expected.equals(run.getStatus())) {
			throw new IllegalStateException("当前 Run 状态不能执行此操作");
		}
	}

	private void recordStep(
			AgentRun run,
			String stepKey,
			String skillCode,
			String resultCode) {
		if (steps.findByRunIdAndStepKey(run.getId(), stepKey).isPresent()) {
			return;
		}
		AgentStep step = new AgentStep();
		step.setRunId(run.getId());
		step.setStepNo(steps.findByRunIdOrderByStepNo(run.getId()).size() + 1);
		step.setSkillCode(skillCode);
		step.setStepKey(stepKey);
		step.setPlanVersion(run.getPlanVersion());
		step.setState("SUCCEEDED".equals(resultCode) || "OK".equals(resultCode)
				? "SUCCEEDED" : "FAILED");
		step.setResultCode(resultCode);
		step.setInputDigest(digest(stepKey));
		step.setStartedAt(LocalDateTime.now());
		step.setFinishedAt(LocalDateTime.now());
		step.setDurationMs(0L);
		steps.save(step);
	}

	private SchedulingAiRunView view(AgentRun run) {
		return new SchedulingAiRunView(
				run.getId(),
				run.getClientRequestId(),
				run.getStatus(),
				run.getPlanVersion(),
				readPlan(run.getParsedPlanJson()),
				run.getRelatedJobId(),
				run.getErrorCode(),
				run.getErrorMessage(),
				run.getExpiresAt(),
				steps.findByRunIdOrderByStepNo(run.getId()));
	}

	private SchedulingAiPlan readPlan(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return json.readValue(value, SchedulingAiPlan.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI Run 结构化计划损坏", exception);
		}
	}

	private Set<String> readIds(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		try {
			return Set.copyOf(json.readValue(value, new TypeReference<List<String>>() { }));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("排课候选结果损坏", exception);
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI Run 结构化数据序列化失败", exception);
		}
	}

	private String digest(String value) {
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("摘要算法不可用", exception);
		}
	}
}
