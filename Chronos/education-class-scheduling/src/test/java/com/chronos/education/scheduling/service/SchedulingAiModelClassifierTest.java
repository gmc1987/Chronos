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

	@Test
	void courseBlockClassificationKeepsOriginalClause() {
		String input = "PLC 实训尽量连堂";
		when(models.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
				.thenReturn("""
						{"clauses":[{"text":"PLC 实训尽量连堂","classification":"OFFERING_BLOCK"}]}
						""");
		assertThat(classifier.classify(input)).singleElement().satisfies(clause -> {
			assertThat(clause.text()).isEqualTo(input);
			assertThat(clause.classification()).isEqualTo("OFFERING_BLOCK");
		});
	}

	@Test
	void acceptsTheNarrowNewRuleCategoriesWithoutChangingSourceText() {
		String input = "PLC 实训仅单周；保留现有课表条目 entry-17；张老师减少空档";
		when(models.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
				.thenReturn("""
						{"clauses":[
							{"text":"PLC 实训仅单周","classification":"WEEK_RULE"},
							{"text":"保留现有课表条目 entry-17","classification":"LOCK_ENTRY"},
							{"text":"张老师减少空档","classification":"TEACHER_PRIORITY"}]}
						""");
		assertThat(classifier.classify(input))
				.extracting(SchedulingAiModelClassifier.Clause::classification)
				.containsExactly("WEEK_RULE", "LOCK_ENTRY", "TEACHER_PRIORITY");
	}

	@Test
	void composesGroundedMultiSlotRuleWithoutAcceptingInventedFields() {
		String input = "张老师周三第1节和第2节不要上课";
		when(models.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
				.thenReturn("""
						{"clauses":[{"text":"张老师周三第1节和第2节不要上课",
						"classification":"SLOT_RULE","rule":{"subject":"TEACHER",
						"reference":"张老师","action":"FORBID","days":[3],"periods":[1,2]}}]}
						""");
		assertThat(classifier.classify(input)).singleElement().satisfies(clause -> {
			assertThat(clause.rule().subject()).isEqualTo("TEACHER");
			assertThat(clause.rule().periods()).containsExactly(1, 2);
		});
	}
}
