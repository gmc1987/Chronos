package com.chronos.agent;

import java.time.Instant;
import java.util.Map;

/**
 * Trusted execution context. Actor, tenant and scope are supplied by the
 * server and must never be copied from model output.
 */
public record ToolContext(
		String runId,
		String actorUsername,
		String schoolId,
		Map<String, Object> dataScope,
		String idempotencyKey,
		Instant deadline) {
	public ToolContext {
		if (runId == null || runId.isBlank()) {
			throw new IllegalArgumentException("runId不能为空");
		}
		if (actorUsername == null || actorUsername.isBlank()) {
			throw new IllegalArgumentException("actorUsername不能为空");
		}
		dataScope = dataScope == null ? Map.of() : Map.copyOf(dataScope);
	}
}
