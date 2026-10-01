package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingAgentCandidateServiceTest {
	private final ScheduleGenerationJobService jobs = mock(ScheduleGenerationJobService.class);
	private final AutoSchedulingService scheduling = mock(AutoSchedulingService.class);
	private final SchedulingAgentCandidateService service = new SchedulingAgentCandidateService(
			jobs, scheduling, mock(EducationDataScopeService.class), new ObjectMapper());

	@Test
	void explanationCandidateChecksMembershipBeforeLookingUpMetrics() {
		AgentRun run = run();
		when(jobs.require("job-1")).thenReturn(job("[\"ours\"]"));
		assertThatThrownBy(() -> service.candidate(run, "admin", "someone-elses"))
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
		verifyNoInteractions(scheduling);
	}

	@Test
	void previewRejectsCandidateFromAnotherRun() {
		AgentRun run = run();
		when(jobs.require("job-1")).thenReturn(job("[\"ours\"]"));

		assertThatThrownBy(() -> service.preview(run, "admin", "someone-elses"))
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
		verifyNoInteractions(scheduling);
	}

	@Test
	void comparisonRequiresMatchingRunResultsAndNoDuplicates() {
		AgentRun run = run();
		when(jobs.require("job-1")).thenReturn(job("[\"ours\",\"other\"]"));

		service.compare(run, "admin", List.of("ours", "other"));
		verify(scheduling).compare(List.of("ours", "other"));
		assertThatThrownBy(() -> service.compare(run, "admin", List.of("ours", "ours")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private AgentRun run() {
		AgentRun run = new AgentRun();
		run.setId("run-1");
		run.setOwnerUsername("admin");
		run.setRelatedJobId("job-1");
		run.setSemesterCode("2026-2027-1");
		return run;
	}

	private ScheduleGenerationJob job(String result) {
		ScheduleGenerationJob job = new ScheduleGenerationJob();
		job.setAgentRunId("run-1");
		job.setRequestedBy("admin");
		job.setSemesterCode("2026-2027-1");
		job.setStatus("SUCCEEDED");
		job.setResultCandidateIds(result);
		return job;
	}
}
