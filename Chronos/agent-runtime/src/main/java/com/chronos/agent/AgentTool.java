package com.chronos.agent;

/** A typed, allow-listed tool callable by a governed agent run. */
public interface AgentTool<I, O> {
	String code();

	int inputSchemaVersion();

	ToolRiskLevel riskLevel();

	ToolResult<O> execute(ToolContext context, I input);
}
