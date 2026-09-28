package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.agent.AgentRunBudget;
import com.chronos.agent.AgentToolExecutor;
import com.chronos.agent.ToolResult;
import com.chronos.education.scheduling.dao.AgentRunRepository;
import com.chronos.education.scheduling.dao.AgentStepRepository;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.AgentStep;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.model.SchedulingAiConfirmRequest;
import com.chronos.education.scheduling.model.SchedulingAiConstraint;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiReplyRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SchedulingAiRunServiceTest {
	private AgentRunRepository runs;
	private AgentStepRepository steps;
	private SchedulingAiRequirementParser parser;
	private ScheduleGenerationJobService jobs;
	private AutoSchedulingService autoScheduling;
	private EducationDataScopeService scopes;
	private SchedulingAiRunService service;
	private AgentToolExecutor tools;
	private SchedulingAgentPlanValidator planValidator;
	private SchedulingAiPlan readyPlan;

	@BeforeEach
	void setUp() {
		runs = mock(AgentRunRepository.class);
		steps = mock(AgentStepRepository.class);
		parser = mock(SchedulingAiRequirementParser.class);
		jobs = mock(ScheduleGenerationJobService.class);
		autoScheduling = mock(AutoSchedulingService.class);
		scopes = mock(EducationDataScopeService.class);
		tools = mock(AgentToolExecutor.class);
		planValidator = new SchedulingAgentPlanValidator(scopes,
				new ObjectMapper().findAndRegisterModules());
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(tools.newBudget(any(), any())).thenReturn(mock(AgentRunBudget.class));
		org.mockito.Mockito.doReturn(ToolResult.success(new SchedulingAgentCatalogService.Page(
				List.of(new SchedulingAgentCatalogService.Item("term-1", "2026-2027-1", "秋季")), 0, 10, false),
				"2026-2027-1", "已查询")).when(tools).invoke(
				org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.REQUIREMENTS),
				org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.CONTEXT),
				org.mockito.ArgumentMatchers.eq(1), any(), any(), any(), any());
		org.mockito.Mockito.doReturn(ToolResult.success("job-1", "2026-2027-1", "已提交"))
				.when(tools).invoke(org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.GENERATION),
				org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.GENERATE),
				org.mockito.ArgumentMatchers.eq(1), any(), any(), any(), any());
		org.mockito.Mockito.doReturn(ToolResult.success(
				new SchedulingAgentCapabilities.Validation(1, 1), "2026-2027-1", "已验证"))
				.when(tools).invoke(
						org.mockito.ArgumentMatchers.any(),
						org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.CONSTRAINTS_VALIDATE),
						org.mockito.ArgumentMatchers.eq(1), any(), any(), any(), any());
		org.mockito.Mockito.doReturn(ToolResult.success(
				new SchedulingAgentCapabilities.Validation(1, 1), "2026-2027-1", "已验证"))
				.when(tools).invoke(
						org.mockito.ArgumentMatchers.any(),
						org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.GENERATION_VALIDATE),
						org.mockito.ArgumentMatchers.eq(1), any(), any(), any(), any());
		service = new SchedulingAiRunService(
				runs,
				steps,
				parser,
				jobs,
				autoScheduling,
				scopes,
				new ObjectMapper().findAndRegisterModules(),
				tools,
				mock(SchedulingAgentCandidateService.class),
				new TransactionTemplate(manager));
		readyPlan = new SchedulingAiPlan(
				1,
				"SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1",
				"GLOBAL",
				Set.of(),
				1,
				List.of(new SchedulingAiConstraint(
						"TEACHER_TIME", "HARD", "teacher-1", "张老师",
						3, 3, null, "服务端解析", "RESOLVED")),
				List.of(),
				List.of());
		when(steps.findByRunIdOrderByStepNo(any())).thenReturn(List.of());
		when(steps.findByRunIdAndStepKey(any(), any())).thenReturn(Optional.empty());
		when(steps.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(runs.save(any())).thenAnswer(invocation -> {
			AgentRun run = invocation.getArgument(0);
			if (run.getId() == null) {
				run.setId("run-1");
			}
			return run;
		});
	}

	@Test
	void sameOwnerAndClientRequestIsIdempotent() {
		SchedulingAiRunRequest request = request("request-1");
		when(parser.requestHash(request)).thenReturn("hash-1");
		when(parser.parse(request, "admin")).thenReturn(parsed(readyPlan));
		when(runs.findByOwnerUsernameAndClientRequestId("admin", "request-1"))
				.thenReturn(Optional.empty(), Optional.empty(), Optional.of(existing("hash-1")));

		var first = service.create(request, "admin");
		var second = service.create(request, "admin");

		assertThat(first.id()).isEqualTo("run-1");
		assertThat(second.id()).isEqualTo("run-existing");
		verify(parser, org.mockito.Mockito.times(1)).parse(request, "admin");
	}

	@Test
	void illegalReplyStateIsRejected() {
		AgentRun run = existing("hash-1");
		run.setId("run-1");
		run.setStatus("READY_FOR_CONFIRMATION");
		run.setPlanVersion(1);
		when(runs.findLockedById("run-1")).thenReturn(Optional.of(run));
		when(runs.findById("run-1")).thenReturn(Optional.of(run));

		assertThatThrownBy(() -> service.reply(
				"run-1",
				new SchedulingAiReplyRequest("补充信息", 1),
				"admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("状态");
	}

	@Test
	void replyCannotSilentlyDropAnUnsupportedOriginalRule() throws Exception {
		AgentRun run = existing("hash-1");
		run.setId("run-1");
		run.setStatus("NEEDS_CLARIFICATION");
		run.setParsedPlanJson(new ObjectMapper().findAndRegisterModules().writeValueAsString(
				new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1", "2026-2027-1",
						"GLOBAL", Set.of(), 1, List.of(), List.of(),
						List.of("暂不支持该需求：数学课尽量安排在上午"))));
		when(runs.findById("run-1")).thenReturn(Optional.of(run));
		when(runs.findLockedById("run-1")).thenReturn(Optional.of(run));
		when(parser.parse(any(SchedulingAiRunRequest.class), org.mockito.ArgumentMatchers.eq("admin")))
				.thenReturn(parsed(new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
						"2026-2027-1", "GLOBAL", Set.of(), 1,
						List.of(), List.of(), List.of())));

		var result = service.reply("run-1", new SchedulingAiReplyRequest("帮我排课", 1), "admin");

		assertThat(result.status()).isEqualTo("NEEDS_CLARIFICATION");
		assertThat(result.plan().unsupported()).contains("暂不支持该需求：数学课尽量安排在上午");
	}

	@Test
	void generateAssociatesExactlyOneAsyncJobAndConfirmBindsPlanVersion() {
		AgentRun run = existing("hash-1");
		run.setId("run-1");
		run.setStatus("READY_FOR_CONFIRMATION");
		run.setPlanVersion(1);
		String planJson;
		try {
			planJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(readyPlan);
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
		run.setParsedPlanJson(planJson);
		when(runs.findLockedById("run-1")).thenReturn(Optional.of(run));

		service.confirm("run-1", new SchedulingAiConfirmRequest(1), "admin");
		run.setStatus("CONFIRMED");
		when(scopes.resolve("admin")).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
		var result = service.generate("run-1", "admin");

		assertThat(result.relatedJobId()).isEqualTo("job-1");
		assertThat(result.status()).isEqualTo("QUEUED");
		var parameters = planValidator.fromConfirmedRun(run, "admin");
		assertThat(parameters.constraints().teacherSlots()).containsExactly(
				new ScheduleRunConstraints.TeacherSlot("teacher-1", 3, 3, "FORBIDDEN"));
		verify(tools).invoke(org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.GENERATION),
				org.mockito.ArgumentMatchers.eq(SchedulingAgentCapabilities.GENERATE),
				org.mockito.ArgumentMatchers.eq(1), any(), any(), any(), any());
	}

	private SchedulingAiRunRequest request(String id) {
		return new SchedulingAiRunRequest(id, "2026-2027-1", "GLOBAL", Set.of(), 1, "排课");
	}

	private SchedulingAiRequirementParser.ParsedRequirement parsed(SchedulingAiPlan plan) {
		return new SchedulingAiRequirementParser.ParsedRequirement(
				plan,
				new AutoScheduleCommand(
						plan.semesterCode(), "AI", "FULL", Set.of(), 1, 5, 8, 1, 20));
	}

	private AgentRun existing(String hash) {
		AgentRun run = new AgentRun();
		run.setId("run-existing");
		run.setClientRequestId("request-1");
		run.setOwnerUsername("admin");
		run.setSemesterCode("2026-2027-1");
		run.setRequestHash(hash);
		run.setStatus("READY_FOR_CONFIRMATION");
		run.setPlanVersion(1);
		run.setParsedPlanJson("{}");
		return run;
	}
}
