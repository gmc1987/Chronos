package com.chronos.agent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Central legal-transition table shared by domain adapters. */
public final class AgentRunStateMachine {
	private static final Map<AgentRunStatus, Set<AgentRunStatus>> TRANSITIONS =
			new EnumMap<>(Map.of(
					AgentRunStatus.DRAFT, EnumSet.of(
							AgentRunStatus.NEEDS_CLARIFICATION,
							AgentRunStatus.READY_FOR_CONFIRMATION,
							AgentRunStatus.CANCELLED,
							AgentRunStatus.EXPIRED),
					AgentRunStatus.NEEDS_CLARIFICATION, EnumSet.of(
							AgentRunStatus.NEEDS_CLARIFICATION,
							AgentRunStatus.READY_FOR_CONFIRMATION,
							AgentRunStatus.CANCELLED,
							AgentRunStatus.EXPIRED),
					AgentRunStatus.READY_FOR_CONFIRMATION, EnumSet.of(
							AgentRunStatus.CONFIRMED,
							AgentRunStatus.NEEDS_CLARIFICATION,
							AgentRunStatus.CANCELLED,
							AgentRunStatus.EXPIRED),
					AgentRunStatus.CONFIRMED, EnumSet.of(
							AgentRunStatus.QUEUED,
							AgentRunStatus.CANCELLED,
							AgentRunStatus.EXPIRED),
					AgentRunStatus.QUEUED, EnumSet.of(
							AgentRunStatus.RUNNING,
							AgentRunStatus.FAILED,
							AgentRunStatus.CANCELLED,
							AgentRunStatus.EXPIRED),
					AgentRunStatus.RUNNING, EnumSet.of(
							AgentRunStatus.CANDIDATES_READY,
							AgentRunStatus.FAILED,
							AgentRunStatus.CANCELLED,
							AgentRunStatus.EXPIRED),
					AgentRunStatus.CANDIDATES_READY, EnumSet.noneOf(AgentRunStatus.class),
					AgentRunStatus.FAILED, EnumSet.noneOf(AgentRunStatus.class),
					AgentRunStatus.CANCELLED, EnumSet.noneOf(AgentRunStatus.class),
					AgentRunStatus.EXPIRED, EnumSet.noneOf(AgentRunStatus.class)));

	private AgentRunStateMachine() {
	}

	public static boolean canTransition(AgentRunStatus from, AgentRunStatus to) {
		return from != null && to != null
				&& (from == to || TRANSITIONS.getOrDefault(from, Set.of()).contains(to));
	}

	public static void assertTransition(AgentRunStatus from, AgentRunStatus to) {
		if (!canTransition(from, to)) {
			throw new IllegalStateException("非法 Agent Run 状态迁移：" + from + " -> " + to);
		}
	}
}
