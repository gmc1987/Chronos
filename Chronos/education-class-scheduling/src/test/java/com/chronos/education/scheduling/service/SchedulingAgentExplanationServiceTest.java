package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.ai.service.AiStructuredOutputException;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.ScheduleCandidateMetrics;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class SchedulingAgentExplanationServiceTest {
	private final SchedulingAgentCandidateService candidates = mock(SchedulingAgentCandidateService.class);
	private final AiModelChatService models = mock(AiModelChatService.class);
	private final SchedulingAgentExplanationService service =
			new SchedulingAgentExplanationService(candidates, models, new ObjectMapper());
	private final AgentRun run = new AgentRun();

	@Test
	void onlyRealFactsReachUserAndUnscheduledWorkIsAlwaysDisclosed() {
		when(candidates.candidate(run, "operator", "ours")).thenReturn(candidate());
		when(models.chatStructured(isNull(), eq("schedule.candidate.facts.v1"), anyString()))
				.thenReturn("{\"factKeys\":[\"BLOCK\",\"SCHEDULED\"]}");
		var result = service.explain(run, "operator", "ours");
		assertThat(result.candidateId()).isEqualTo("ours");
		assertThat(result.facts()).extracting(fact -> fact.code())
				.containsExactly("UNSCHEDULED", "BLOCK", "SCHEDULED");
		assertThat(result.facts()).extracting(fact -> fact.value()).containsExactly(2, 3, 18);
	}

	@Test
	void invalidModelClaimsFailInsteadOfDisplayingFabricatedFacts() {
		when(candidates.candidate(run, "operator", "ours")).thenReturn(candidate());
		when(models.chatStructured(isNull(), eq("schedule.candidate.facts.v1"), anyString()))
				.thenReturn("{\"factKeys\":[\"PERFECT_SCHEDULE\"]}");
		assertThatThrownBy(() -> service.explain(run, "operator", "ours"))
				.isInstanceOf(AiStructuredOutputException.class);
	}

	@Test
	void unauthorizedCandidateNeverReachesModel() {
		when(candidates.candidate(run, "operator", "other"))
				.thenThrow(new AccessDeniedException("not yours"));
		assertThatThrownBy(() -> service.explain(run, "operator", "other"))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(models);
	}

	private ScheduleCandidateView candidate() {
		return new ScheduleCandidateView("ours", "term", null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null,
				new ScheduleCandidateMetrics(18, 2, 4, 0, 0, 0, 0, 0, 0, 3));
	}
}
