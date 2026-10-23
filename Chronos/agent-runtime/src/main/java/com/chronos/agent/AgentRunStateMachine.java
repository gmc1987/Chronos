package com.chronos.agent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Central legal-transition table shared by domain adapters. */
public final class AgentRunStateMachine {
	private static final Map<AgentRunStatus, Set<AgentRunStatus>> TRANSITIONS = transitions();

	private AgentRunStateMachine() {
	}

	private static Map<AgentRunStatus, Set<AgentRunStatus>> transitions() {
		Map<AgentRunStatus, Set<AgentRunStatus>> values = new EnumMap<>(AgentRunStatus.class);
		values.put(AgentRunStatus.DRAFT, EnumSet.of(
				AgentRunStatus.NEEDS_CLARIFICATION, AgentRunStatus.READY_FOR_CONFIRMATION,
				AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.NEEDS_CLARIFICATION, EnumSet.of(
				AgentRunStatus.NEEDS_CLARIFICATION, AgentRunStatus.READY_FOR_CONFIRMATION,
				AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.READY_FOR_CONFIRMATION, EnumSet.of(
				AgentRunStatus.CONFIRMED, AgentRunStatus.NEEDS_CLARIFICATION,
				AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.CONFIRMED, EnumSet.of(
				AgentRunStatus.QUEUED, AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.QUEUED, EnumSet.of(
				AgentRunStatus.RUNNING, AgentRunStatus.FAILED,
				AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.RUNNING, EnumSet.of(
				AgentRunStatus.PAUSE_REQUESTED, AgentRunStatus.WAITING_APPROVAL,
				AgentRunStatus.CANDIDATES_READY, AgentRunStatus.COMPLETED,
				AgentRunStatus.FAILED, AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.PAUSE_REQUESTED, EnumSet.of(
				AgentRunStatus.PAUSED, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED));
		values.put(AgentRunStatus.PAUSED, EnumSet.of(
				AgentRunStatus.REPLANNING, AgentRunStatus.QUEUED,
				AgentRunStatus.FAILED, AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.REPLANNING, EnumSet.of(
				AgentRunStatus.PAUSED, AgentRunStatus.QUEUED,
				AgentRunStatus.FAILED, AgentRunStatus.CANCELLED));
		values.put(AgentRunStatus.WAITING_APPROVAL, EnumSet.of(
				AgentRunStatus.QUEUED, AgentRunStatus.PAUSED,
				AgentRunStatus.FAILED, AgentRunStatus.CANCELLED, AgentRunStatus.EXPIRED));
		values.put(AgentRunStatus.CANDIDATES_READY, EnumSet.noneOf(AgentRunStatus.class));
		values.put(AgentRunStatus.COMPLETED, EnumSet.noneOf(AgentRunStatus.class));
		values.put(AgentRunStatus.FAILED, EnumSet.of(
				AgentRunStatus.QUEUED, AgentRunStatus.CANCELLED));
		values.put(AgentRunStatus.CANCELLED, EnumSet.noneOf(AgentRunStatus.class));
		values.put(AgentRunStatus.EXPIRED, EnumSet.noneOf(AgentRunStatus.class));
		return Map.copyOf(values);
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
