package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.ai.service.AiStructuredOutputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SchedulingAiModelClassifierTest {
	private final AiModelChatService models = mock(AiModelChatService.class);
	private final SchedulingAiModelClassifier classifier =
			new SchedulingAiModelClassifier(models, new ObjectMapper());

	@Test
	void eachOriginalClauseMustBeRepresentedInOrder() {
		String input = "帮我排课；张老师周三第3节不能上课";
		when(models.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
				.thenReturn("""
						{"clauses":[
							{"text":"帮我排课","classification":"GENERATION"},
							{"text":"张老师周三第3节不能上课","classification":"TEACHER_SLOT"}]}
						""");
		assertThat(classifier.classify(input)).extracting(SchedulingAiModelClassifier.Clause::text)
				.containsExactly("帮我排课", "张老师周三第3节不能上课");

		when(models.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
				.thenReturn("""
						{"clauses":[{"text":"帮我排课","classification":"GENERATION"}]}
						""");
		assertThatThrownBy(() -> classifier.classify(input))
				.isInstanceOf(AiStructuredOutputException.class)
				.hasMessageContaining("遗漏");
	}

	@Test
	void modelCannotInventToolCodesOrReplaceUserText() {
		String input = "张老师周三第3节不能上课";
		when(models.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
				.thenReturn("""
						{"clauses":[{"text":"李老师周三第3节不能上课","classification":"TEACHER_SLOT"}]}
						""");
		assertThatThrownBy(() -> classifier.classify(input))
				.isInstanceOf(AiStructuredOutputException.class)
				.hasMessageContaining("原文");
	}
}
