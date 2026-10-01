package com.chronos.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentRunStateMachineTest {
	@Test
	void allowsOnlyGovernedLifecycleTransitions() {
		assertThat(AgentRunStateMachine.canTransition(
				AgentRunStatus.CONFIRMED, AgentRunStatus.QUEUED)).isTrue();
		assertThat(AgentRunStateMachine.canTransition(
				AgentRunStatus.CANDIDATES_READY, AgentRunStatus.CONFIRMED)).isFalse();
		assertThatThrownBy(() -> AgentRunStateMachine.assertTransition(
				AgentRunStatus.CANDIDATES_READY, AgentRunStatus.CONFIRMED))
				.isInstanceOf(IllegalStateException.class);
	}
}
