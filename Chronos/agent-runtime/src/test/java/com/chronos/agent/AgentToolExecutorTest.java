package com.chronos.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AgentToolExecutorTest {
	private final AtomicInteger executions = new AtomicInteger();
	private final AgentTool<String, Integer> readTool = tool("read", ToolRiskLevel.READ_ONLY);
	private final AgentTool<String, Integer> draftTool = tool("draft", ToolRiskLevel.DRAFT_WRITE);
	private final AgentTool<String, Integer> writeTool = tool("write", ToolRiskLevel.CONFIRMED_WRITE);
	private final AgentSkill skill = skill("schedule", Set.of("read", "write"), 2);

	@Test
	void springDiscoversRegistryAndExecutorAndEnforcesAllowListAndSchema() {
		try (var spring = new AnnotationConfigApplicationContext()) {
			spring.registerBean("skill", AgentSkill.class, () -> skill);
			spring.registerBean("readTool", AgentTool.class, () -> readTool);
			spring.registerBean("writeTool", AgentTool.class, () -> writeTool);
			spring.register(AgentRegistry.class, AgentToolExecutor.class);
			spring.refresh();

			AgentToolExecutor executor = spring.getBean(AgentToolExecutor.class);
			AgentRunBudget budget = executor.newBudget(context("run", "1", Instant.now().plusSeconds(60)), "schedule");
			assertThat(executor.invoke("schedule", "read", 1, context("run", "1"), budget, null, "value")
					.succeeded()).isTrue();
			assertCode(executor.invoke("schedule", "write", 2, context("run", "2"), budget,
					AgentRunStatus.RUNNING, "value"), "UNSUPPORTED_INPUT_SCHEMA");
			assertCode(executor.invoke("schedule", "missing", 1, context("run", "2"), budget,
					null, "value"), "UNKNOWN_TOOL");
			assertCode(executor.invoke("schedule", null, 1, context("run", "2"), budget,
					null, "value"), "UNKNOWN_TOOL");
			assertCode(executor.invoke("missing", "read", 1, context("run", "2"), budget,
					null, "value"), "UNKNOWN_SKILL");
			assertCode(executor.invoke(null, "read", 1, context("run", "2"), budget,
					null, "value"), "UNKNOWN_SKILL");
			assertThat(budget.usedCalls()).isEqualTo(1);
			assertThat(executions).hasValue(1);
		}
		AgentToolExecutor limited = executor(List.of(skill("limited", Set.of("read"), 2)),
				List.of(readTool, writeTool), List.of());
		AgentRunBudget budget = limited.newBudget(context("run", "1"), "limited");
		assertCode(limited.invoke("limited", "write", 1, context("run", "1"), budget,
				AgentRunStatus.RUNNING, "value"), "TOOL_NOT_ALLOWED");
		assertThat(budget.usedCalls()).isZero();
	}

	@Test
	void deniesWritesWithoutBothConfirmedPlanAndServerAuthorization() {
		AgentToolExecutor denied = executor(List.of(skill), List.of(readTool, writeTool), List.of());
		AgentRunBudget budget = denied.newBudget(context("run", "write"), "schedule");
		assertCode(denied.invoke("schedule", "write", 1, context("run", "write"), budget,
				AgentRunStatus.RUNNING, "value"), "AUTHORIZATION_REQUIRED");

		AgentToolExecutor permitted = executor(List.of(skill), List.of(readTool, writeTool),
				List.of((context, skillCode, toolCode) -> true));
		AgentRunBudget permittedBudget = permitted.newBudget(context("run", "write"), "schedule");
		assertCode(permitted.invoke("schedule", "write", 1, context("run", "write"), permittedBudget,
				AgentRunStatus.DRAFT, "value"), "AUTHORIZATION_REQUIRED");
		assertThat(permitted.invoke("schedule", "write", 1, context("run", "write"), permittedBudget,
				AgentRunStatus.CONFIRMED, "value").succeeded()).isTrue();
		assertThat(budget.usedCalls()).isZero();
		assertThat(permittedBudget.usedCalls()).isEqualTo(1);
		assertThat(executions).hasValue(1);
	}

	@Test
	void draftWriteAllowsReadyForConfirmationOnlyWithServerAuthorization() {
		AgentSkill draftSkill = skill("draft-skill", Set.of("draft", "write"), 2);
		AgentToolExecutor denied = executor(List.of(draftSkill), List.of(draftTool, writeTool), List.of());
		AgentRunBudget deniedBudget = denied.newBudget(context("run", "one"), "draft-skill");
		assertCode(denied.invoke("draft-skill", "draft", 1, context("run", "one"), deniedBudget,
				AgentRunStatus.READY_FOR_CONFIRMATION, "input"), "AUTHORIZATION_REQUIRED");
		assertThat(deniedBudget.usedCalls()).isZero();

		AgentToolExecutor permitted = executor(List.of(draftSkill), List.of(draftTool, writeTool),
				List.of((context, skillCode, toolCode) -> true));
		AgentRunBudget budget = permitted.newBudget(context("run", "one"), "draft-skill");
		assertCode(permitted.invoke("draft-skill", "draft", 1, context("run", "one"), budget,
				AgentRunStatus.DRAFT, "input"), "AUTHORIZATION_REQUIRED");
		assertCode(permitted.invoke("draft-skill", "write", 1, context("run", "one"), budget,
				AgentRunStatus.READY_FOR_CONFIRMATION, "input"), "AUTHORIZATION_REQUIRED");
		assertThat(permitted.invoke("draft-skill", "draft", 1, context("run", "one"), budget,
				AgentRunStatus.READY_FOR_CONFIRMATION, "input").succeeded()).isTrue();
		assertThat(budget.usedCalls()).isEqualTo(1);
		assertThat(executions).hasValue(1);
	}

	@Test
	void springRegistryStartsWithNoToolsAndAnEmptyAllowList() {
		try (var spring = new AnnotationConfigApplicationContext()) {
			spring.registerBean("emptySkill", AgentSkill.class, () -> skill("empty", Set.of(), 0));
			spring.register(AgentRegistry.class, AgentToolExecutor.class);
			spring.refresh();
			AgentRegistry registry = spring.getBean(AgentRegistry.class);
			assertThat(registry.skill("empty")).isNotNull();
			assertThat(registry.tool("missing")).isNull();
			AgentToolExecutor executor = spring.getBean(AgentToolExecutor.class);
			AgentRunBudget budget = executor.newBudget(context("empty-run", "one"), "empty");
			assertCode(executor.invoke("empty", "missing", 1, context("empty-run", "one"),
					budget, null, null), "UNKNOWN_TOOL");
		}
	}

	@Test
	void rejectsDuplicateAndInvalidRegistrations() {
		assertThatThrownBy(() -> new AgentRegistry(List.of(skill("one", Set.of(), 1),
				skill("one", Set.of(), 1)), List.of())).hasMessageContaining("重复的技能");
		assertThatThrownBy(() -> new AgentRegistry(List.of(),
				List.of(readTool, tool("read", ToolRiskLevel.READ_ONLY)))).hasMessageContaining("重复的工具");
		assertThatThrownBy(() -> new AgentRegistry(List.of(skill("one", Set.of("missing"), 1)),
				List.of(readTool))).hasMessageContaining("未注册的工具");
	}

	@Test
	void countsUniqueLogicalCallsPerRunAndSkillWithoutDoubleChargingRetries() {
		AgentToolExecutor executor = executor(List.of(skill("limited", Set.of("read"), 2)),
				List.of(readTool), List.of());
		AgentRunBudget first = executor.newBudget(context("run-1", "1"), "limited");
		ToolResult<?> result = executor.invoke("limited", "read", 1, context("run-1", "1"),
				first, null, "same");
		assertThat(result.succeeded()).isTrue();
		assertThat(executor.invoke("limited", "read", 1, context("run-1", "1"),
				first, null, "same")).isSameAs(result);
		assertCode(executor.invoke("limited", "read", 1, context("run-1", "1"),
				first, null, "different"), "IDEMPOTENCY_KEY_CONFLICT");
		assertThat(executor.invoke("limited", "read", 1, context("run-1", "2"),
				first, null, "second").succeeded()).isTrue();
		assertCode(executor.invoke("limited", "read", 1, context("run-1", "3"),
				first, null, "third"), "TOOL_BUDGET_EXCEEDED");
		assertCode(executor.invoke("limited", "read", 1, context("run-2", "4"),
				first, null, "wrong run"), "INVALID_TOOL_CONTEXT");
		AgentRunBudget second = executor.newBudget(context("run-2", "1"), "limited");
		assertThat(executor.invoke("limited", "read", 1, context("run-2", "1"),
				second, null, "new run").succeeded()).isTrue();
		assertThat(first.usedCalls()).isEqualTo(2);
		assertThat(second.usedCalls()).isEqualTo(1);
		assertThat(executions).hasValue(3);
	}

	@Test
	void failedToolResultCanBeRetriedWithoutConsumingAnotherCall() {
		AtomicInteger attempts = new AtomicInteger();
		AgentTool<String, Integer> unstable = new AgentTool<>() {
			@Override
			public String code() {
				return "unstable";
			}

			@Override
			public int inputSchemaVersion() {
				return 1;
			}

			@Override
			public ToolRiskLevel riskLevel() {
				return ToolRiskLevel.READ_ONLY;
			}

			@Override
			public ToolResult<Integer> execute(ToolContext context, String input) {
				int attempt = attempts.incrementAndGet();
				return attempt == 1 ? ToolResult.failure("TEMPORARY_FAILURE", "retry")
						: ToolResult.success(attempt, "1", input);
			}
		};
		AgentToolExecutor executor = executor(List.of(skill("limited", Set.of("unstable"), 1)),
				List.of(unstable), List.of());
		AgentRunBudget budget = executor.newBudget(context("run", "one"), "limited");
		assertCode(executor.invoke("limited", "unstable", 1, context("run", "one"),
				budget, null, "input"), "TEMPORARY_FAILURE");
		assertThat(executor.invoke("limited", "unstable", 1, context("run", "one"),
				budget, null, "input").succeeded()).isTrue();
		assertCode(executor.invoke("limited", "unstable", 1, context("run", "two"),
				budget, null, "input"), "TOOL_BUDGET_EXCEEDED");
		assertThat(budget.usedCalls()).isEqualTo(1);
		assertThat(attempts).hasValue(2);
	}

	@Test
	void rejectsExpiredOrMissingDeadlineAndIdempotencyKeyBeforeExecution() {
		AgentToolExecutor executor = executor(List.of(skill), List.of(readTool, writeTool), List.of());
		AgentRunBudget budget = executor.newBudget(context("run", "1"), "schedule");
		assertCode(executor.invoke("schedule", "read", 1,
				context("run", "1", Instant.now().minusSeconds(1)), budget, null, "value"),
				"DEADLINE_EXPIRED");
		assertCode(executor.invoke("schedule", "read", 1, context("run", "1", null),
				budget, null, "value"), "DEADLINE_EXPIRED");
		assertCode(executor.invoke("schedule", "read", 1, context("run", ""),
				budget, null, "value"), "INVALID_TOOL_CONTEXT");
		assertThat(budget.usedCalls()).isZero();
		assertThat(executions).hasValue(0);
	}

	private AgentToolExecutor executor(List<AgentSkill> skills, List<AgentTool<?, ?>> tools,
			List<ToolAuthorizationPolicy> policies) {
		return new AgentToolExecutor(new AgentRegistry(skills, tools), policies);
	}

	private AgentTool<String, Integer> tool(String code, ToolRiskLevel risk) {
		return new AgentTool<>() {
			@Override
			public String code() {
				return code;
			}

			@Override
			public int inputSchemaVersion() {
				return 1;
			}

			@Override
			public ToolRiskLevel riskLevel() {
				return risk;
			}

			@Override
			public ToolResult<Integer> execute(ToolContext context, String input) {
				return ToolResult.success(executions.incrementAndGet(), "1", input);
			}
		};
	}

	private static AgentSkill skill(String code, Set<String> allowed, int limit) {
		return new AgentSkill() {
			@Override
			public String code() {
				return code;
			}

			@Override
			public int schemaVersion() {
				return 1;
			}

			@Override
			public Set<String> allowedToolCodes() {
				return allowed;
			}

			@Override
			public int maxToolCalls() {
				return limit;
			}
		};
	}

	private static ToolContext context(String runId, String key) {
		return context(runId, key, Instant.now().plusSeconds(60));
	}

	private static ToolContext context(String runId, String key, Instant deadline) {
		return new ToolContext(runId, "actor", "school", Map.of(), key, deadline);
	}

	private static void assertCode(ToolResult<?> result, String code) {
		assertThat(result.resultCode()).isEqualTo(code);
		assertThat(result.succeeded()).isFalse();
	}
}
