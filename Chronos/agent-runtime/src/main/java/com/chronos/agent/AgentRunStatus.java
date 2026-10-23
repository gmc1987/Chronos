package com.chronos.agent;

/** Common lifecycle states for a governed agent run. */
public enum AgentRunStatus {
	DRAFT,
	NEEDS_CLARIFICATION,
	READY_FOR_CONFIRMATION,
	CONFIRMED,
	QUEUED,
	RUNNING,
	PAUSE_REQUESTED,
	PAUSED,
	REPLANNING,
	WAITING_APPROVAL,
	CANDIDATES_READY,
	COMPLETED,
	FAILED,
	CANCELLED,
	EXPIRED
}
