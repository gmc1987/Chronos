package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.agent.ToolContext;
import com.chronos.education.scheduling.dao.AgentRunRepository;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchedulingAgentCapabilitiesTest {
	private final AgentRunRepository runs = mock(AgentRunRepository.class);
	private final ScheduleGenerationJobService jobs = mock(ScheduleGenerationJobService.class);
	private final EducationDataScopeService scopes = mock(EducationDataScopeService.class);
	private final SchedulingAgentPlanValidator validator =
			new SchedulingAgentPlanValidator(scopes, new ObjectMapper().findAndRegisterModules());
	private final ToolContext context = new ToolContext("run-1", "admin", null,
			Map.of(), "run-1:submit:1", Instant.now().plusSeconds(10));

	@Test
	void generationToolUsesPersistedConfirmedPlanInsteadOfCallerParameters() throws Exception {
		AgentRun run = run("admin", "CONFIRMED");
		run.setConfirmedPlanJson(new ObjectMapper().findAndRegisterModules()
				.writeValueAsString(new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
						"2026-2027-1", "GLOBAL", Set.of(), 2, List.of(), List.of(), List.of())));
		when(runs.findById("run-1")).thenReturn(Optional.of(run));
		ScheduleGenerationJob job = new ScheduleGenerationJob();
		job.setId("job-1");
		when(jobs.submit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("admin"),
				org.mockito.ArgumentMatchers.eq("run-1"), org.mockito.ArgumentMatchers.any()))
				.thenReturn(job);

		var result = new SchedulingAgentCapabilities.GenerationTool(runs, jobs, validator)
				.execute(context, new SchedulingAgentCapabilities.GenerationInput(1));

		assertThat(result.data()).isEqualTo("job-1");
		org.mockito.ArgumentCaptor<com.chronos.education.scheduling.model.AutoScheduleCommand> captured =
				org.mockito.ArgumentCaptor.forClass(com.chronos.education.scheduling.model.AutoScheduleCommand.class);
		verify(jobs).submit(captured.capture(), org.mockito.ArgumentMatchers.eq("admin"),
				org.mockito.ArgumentMatchers.eq("run-1"), org.mockito.ArgumentMatchers.any());
		assertThat(captured.getValue().candidateCount()).isEqualTo(2);
	}

	@Test
	void unauthorizedOwnerOrStalePlanCannotSubmit() {
		when(runs.findById("run-1")).thenReturn(Optional.of(run("someone-else", "CONFIRMED")));
		var tool = new SchedulingAgentCapabilities.GenerationTool(runs, jobs, validator);
		assertThatThrownBy(() -> tool.execute(context, new SchedulingAgentCapabilities.GenerationInput(1)))
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

		when(runs.findById("run-1")).thenReturn(Optional.of(run("admin", "READY_FOR_CONFIRMATION")));
		assertThatThrownBy(() -> tool.execute(context, new SchedulingAgentCapabilities.GenerationInput(1)))
				.isInstanceOf(IllegalStateException.class);
		org.mockito.Mockito.verifyNoInteractions(jobs);
	}

	@Test
	void contextToolRejectsForeignSemesterBeforeQuery() {
		when(runs.findById("run-1")).thenReturn(Optional.of(run("admin", "READY_FOR_CONFIRMATION")));
		SchedulingAgentCatalogService catalog = mock(SchedulingAgentCatalogService.class);
		var tool = new SchedulingAgentCapabilities.ContextTool(runs, catalog);
		assertThatThrownBy(() -> tool.execute(context,
				new SchedulingAgentCapabilities.ContextInput("TERM", "2025-2026-1", "", 0, 10)))
				.isInstanceOf(IllegalArgumentException.class);
		org.mockito.Mockito.verifyNoInteractions(catalog);
	}

	private AgentRun run(String owner, String status) {
		AgentRun run = new AgentRun();
		run.setId("run-1");
		run.setOwnerUsername(owner);
		run.setSemesterCode("2026-2027-1");
		run.setStatus(status);
		return run;
	}
}
