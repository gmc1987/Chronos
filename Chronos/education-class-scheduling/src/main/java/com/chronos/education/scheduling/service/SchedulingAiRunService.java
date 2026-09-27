package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AgentRunRepository;
import com.chronos.education.scheduling.dao.AgentStepRepository;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.AgentStep;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.SchedulingAiConfirmRequest;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiReplyRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates the persisted state machine without exposing apply/publish tools. */
@Service
public class SchedulingAiRunService {
	private static final int EXPIRY_HOURS = 24;

	private final AgentRunRepository runs;
	private final AgentStepRepository steps;
	private final SchedulingAiRequirementParser parser;
	private final ScheduleGenerationJobService jobs;
	private final AutoSchedulingService autoScheduling;
	private final EducationDataScopeService dataScopes;
	private final ObjectMapper json;

	public SchedulingAiRunService(
			AgentRunRepository runs,
			AgentStepRepository steps,
			SchedulingAiRequirementParser parser,
			ScheduleGenerationJobService jobs,
			AutoSchedulingService autoScheduling,
			EducationDataScopeService dataScopes,
			ObjectMapper json) {
		this.runs = runs;
		this.steps = steps;
		this.parser = parser;
		this.jobs = jobs;
		this.autoScheduling = autoScheduling;
		this.dataScopes = dataScopes;
		this.json = json;
	}

	@Transactional
	public SchedulingAiRunView create(SchedulingAiRunRequest request, String actor) {
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
		recordStep(run, "requirements.parse", "SCHEDULE_REQUIREMENTS_V1", "OK");
		return view(run);
	}

	@Transactional
	public SchedulingAiRunView get(String id, String actor, boolean operations) {
		AgentRun run = requireVisible(id, actor, operations);
		return view(synchronize(run));
	}

	@Transactional
	public SchedulingAiRunView reply(
			String id,
			SchedulingAiReplyRequest request,
			String actor) {
		AgentRun current = lockedOwner(id, actor);
		requireVersion(current, request.expectedPlanVersion());
		requireState(current, "NEEDS_CLARIFICATION");
		SchedulingAiPlan previous = readPlan(current.getParsedPlanJson());
		String priorContext = previous.constraints().stream()
				.map(constraint -> constraint.teacherName() == null
						? "" : constraint.teacherName())
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse("");
		SchedulingAiRunRequest reparsed = new SchedulingAiRunRequest(
				current.getClientRequestId() + "-reply-" + current.getPlanVersion(),
				current.getSemesterCode(),
				previous.mode(),
				previous.selectedOfferingIds(),
				previous.candidateCount(),
				(priorContext + " " + request.answer()).trim());
		SchedulingAiPlan plan = parser.parse(reparsed, actor).plan();
		current.setPlanVersion(current.getPlanVersion() + 1);
		current.setParsedPlanJson(write(plan));
		current.setStatus(plan.readyForConfirmation()
				? "READY_FOR_CONFIRMATION"
				: "NEEDS_CLARIFICATION");
		current.setErrorCode(null);
		current.setErrorMessage(null);
		recordStep(current, "requirements.reply." + current.getPlanVersion(),
				"SCHEDULE_REQUIREMENTS_V1", "OK");
		return view(runs.save(current));
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
		requireState(current, "CONFIRMED");
		SchedulingAiPlan plan = readPlan(current.getConfirmedPlanJson());
		AutoScheduleCommand command = new AutoScheduleCommand(
				plan.semesterCode(),
				"AI-" + plan.semesterCode(),
				"LOCAL".equals(plan.mode()) ? "LOCAL" : "FULL",
				plan.selectedOfferingIds(),
				plan.candidateCount(),
				5,
				8,
				1,
				20);
		ScheduleGenerationJob job = jobs.submit(command, actor, current.getId());
		current.setRelatedJobId(job.getId());
		current.setStatus("QUEUED");
		recordStep(current, "generation.submit." + current.getPlanVersion(),
				"SCHEDULE_GENERATE_V1", "OK");
		return view(runs.save(current));
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
		Set<String> ids = readIds(job.getResultCandidateIds());
		return autoScheduling.list(run.getSemesterCode()).stream()
				.filter(candidate -> ids.contains(candidate.id()))
				.toList();
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
		if (run.getExpiresAt() != null
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
