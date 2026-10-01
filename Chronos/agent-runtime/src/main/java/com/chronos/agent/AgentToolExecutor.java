package com.chronos.agent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * Governs tool invocation before delegating to a registered tool. All context,
 * status, and budgets must come from the trusted server, never from model output.
 */
@Component
public final class AgentToolExecutor {
	private final AgentRegistry registry;
	private final ToolAuthorizationPolicy authorization;

	public AgentToolExecutor(AgentRegistry registry, List<ToolAuthorizationPolicy> policies) {
		this.registry = Objects.requireNonNull(registry);
		if (policies.size() > 1) {
			throw new IllegalArgumentException("只能注册一个工具授权策略");
		}
		this.authorization = policies.isEmpty() ? (context, skill, tool) -> false : policies.getFirst();
	}

	/** Allocate one budget for the lifetime of this run/skill, not per invocation. */
	public AgentRunBudget newBudget(ToolContext context, String skillCode) {
		Objects.requireNonNull(context, "context不能为空");
		AgentSkill skill = registry.skill(skillCode);
		if (skill == null) {
			throw new IllegalArgumentException("未知的技能代码：" + skillCode);
		}
		return new AgentRunBudget(context.runId(), skillCode, skill.maxToolCalls());
	}

	public ToolResult<?> invoke(String skillCode, String toolCode, int inputSchemaVersion,
			ToolContext context, AgentRunBudget budget, AgentRunStatus planStatus, Object input) {
		AgentSkill skill = registry.skill(skillCode);
		if (skill == null) {
			return ToolResult.failure("UNKNOWN_SKILL", "未注册的技能");
		}
		AgentRegistry.RegisteredTool registration = registry.registration(toolCode);
		if (registration == null) {
			return ToolResult.failure("UNKNOWN_TOOL", "未注册的工具");
		}
		if (!skill.allowedToolCodes().contains(toolCode)) {
			return ToolResult.failure("TOOL_NOT_ALLOWED", "技能未授权此工具");
		}
		if (registration.inputSchemaVersion() != inputSchemaVersion) {
			return ToolResult.failure("UNSUPPORTED_INPUT_SCHEMA", "工具输入 schema 版本不受支持");
		}
		if (context == null || budget == null || !budget.matches(context.runId(), skillCode, skill.maxToolCalls())
				|| context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
			return ToolResult.failure("INVALID_TOOL_CONTEXT", "运行上下文或幂等键无效");
		}
		if (context.deadline() == null || !Instant.now().isBefore(context.deadline())) {
			return ToolResult.failure("DEADLINE_EXPIRED", "工具调用已超过截止时间");
		}
		if (registration.riskLevel() != ToolRiskLevel.READ_ONLY) {
			boolean confirmed = planStatus == AgentRunStatus.CONFIRMED
					|| planStatus == AgentRunStatus.QUEUED || planStatus == AgentRunStatus.RUNNING;
			if (!confirmed && (registration.riskLevel() != ToolRiskLevel.DRAFT_WRITE
					|| planStatus != AgentRunStatus.READY_FOR_CONFIRMATION)) {
				return ToolResult.failure("AUTHORIZATION_REQUIRED", "当前运行状态不允许此写工具");
			}
			try {
				if (!authorization.permits(context, skillCode, toolCode)) {
					return ToolResult.failure("AUTHORIZATION_REQUIRED", "工具执行未获服务端授权");
				}
			} catch (RuntimeException ex) {
				return ToolResult.failure("AUTHORIZATION_REQUIRED", "工具授权验证失败");
			}
		}
		return budget.execute(context.idempotencyKey(), toolCode, inputSchemaVersion, input,
				context.deadline(),
				() -> execute(registration.delegate(), context, input));
	}

	@SuppressWarnings("unchecked")
	private static ToolResult<?> execute(AgentTool<?, ?> tool, ToolContext context, Object input) {
		return ((AgentTool<Object, ?>) tool).execute(context, input);
	}
}
