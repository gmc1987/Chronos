package com.chronos.agent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-run, per-skill in-process budget. Keep the same instance for all calls
 * and retries of a run. Retries reuse the idempotency key and a value-equal,
 * immutable input; successful results are cached, failed results may retry
 * without consuming another slot. Distributed/restarted runs require a durable, atomic
 * budget/idempotency store at the caller boundary; creating another budget
 * for the same run does not restore its previous usage.
 */
public final class AgentRunBudget {
	private final String runId;
	private final String skillCode;
	private final int limit;
	private final Map<String, Call> calls = new HashMap<>();

	AgentRunBudget(String runId, String skillCode, int limit) {
		this.runId = runId;
		this.skillCode = skillCode;
		this.limit = limit;
	}

	boolean matches(String runId, String skillCode, int limit) {
		return this.runId.equals(runId) && this.skillCode.equals(skillCode) && this.limit == limit;
	}

	public synchronized int usedCalls() {
		return calls.size();
	}

	synchronized ToolResult<?> execute(String key, String toolCode, int schemaVersion,
			Object input, Instant deadline, ToolInvocation invocation) {
		if (!Instant.now().isBefore(deadline)) {
			return ToolResult.failure("DEADLINE_EXPIRED", "工具调用已超过截止时间");
		}
		Call call = calls.get(key);
		if (call != null && (!call.toolCode.equals(toolCode) || call.schemaVersion != schemaVersion
				|| !Objects.deepEquals(call.input, input))) {
			return ToolResult.failure("IDEMPOTENCY_KEY_CONFLICT", "同一幂等键不能用于不同的工具请求");
		}
		if (call == null) {
			if (calls.size() >= limit) {
				return ToolResult.failure("TOOL_BUDGET_EXCEEDED", "本次运行的工具调用额度已用尽");
			}
			call = new Call(toolCode, schemaVersion, input);
			calls.put(key, call);
		}
		if (call.result != null) {
			return call.result;
		}
		ToolResult<?> result = Objects.requireNonNull(invocation.invoke(), "工具不能返回 null");
		if (result.succeeded()) {
			call.result = result;
		}
		return result;
	}

	@FunctionalInterface
	interface ToolInvocation {
		ToolResult<?> invoke();
	}

	private static final class Call {
		private final String toolCode;
		private final int schemaVersion;
		private final Object input;
		private ToolResult<?> result;

		private Call(String toolCode, int schemaVersion, Object input) {
			this.toolCode = toolCode;
			this.schemaVersion = schemaVersion;
			this.input = input;
		}
	}
}
