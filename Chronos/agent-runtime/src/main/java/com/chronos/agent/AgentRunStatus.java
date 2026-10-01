package com.chronos.agent;

/** Common lifecycle states for a governed agent run. */
public enum AgentRunStatus {
	DRAFT,
	NEEDS_CLARIFICATION,
	READY_FOR_CONFIRMATION,
	CONFIRMED,
	QUEUED,
	RUNNING,
	CANDIDATES_READY,
	FAILED,
	CANCELLED,
	EXPIRED
}
