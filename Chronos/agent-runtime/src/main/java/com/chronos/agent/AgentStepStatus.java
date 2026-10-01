package com.chronos.agent;

/** Durable execution state for one skill/tool step. */
public enum AgentStepStatus {
	QUEUED,
	RUNNING,
	SUCCEEDED,
	FAILED,
	SKIPPED
}
