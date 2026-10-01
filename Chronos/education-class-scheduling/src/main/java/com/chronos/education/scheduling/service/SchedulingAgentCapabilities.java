package com.chronos.education.scheduling.service;

import com.chronos.agent.AgentSkill;
import com.chronos.agent.AgentTool;
import com.chronos.agent.ToolContext;
import com.chronos.agent.ToolResult;
import com.chronos.agent.ToolRiskLevel;
import com.chronos.education.scheduling.dao.AgentRunRepository;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** Explicit allow-lists; no generic domain repository or publication operations are exposed. */
@Configuration
public class SchedulingAgentCapabilities {
	public static final String CONTEXT = "schedule.context.read.v1";
	public static final String RESOLVE = "schedule.entities.resolve.v1";
	public static final String CONSTRAINTS_VALIDATE = "schedule.constraints.validate.v1";
	public static final String GENERATION_VALIDATE = "schedule.generation.validate.v1";
	public static final String STATUS = "schedule.generation.status.v1";
	public static final String COMPARE = "schedule.candidates.compare.v1";
	public static final String PREVIEW = "schedule.candidate.preview.v1";
	public static final String GENERATE = "schedule.generation.submit.v1";
	public static final String REQUIREMENTS = "SCHEDULE_REQUIREMENTS_V1";
	public static final String CONSTRAINTS = "SCHEDULE_CONSTRAINTS_V1";
	public static final String GENERATION = "SCHEDULE_GENERATE_V1";
	public static final String COMPARISON = "SCHEDULE_COMPARE_V1";

	@Bean
	AgentSkill schedulingRequirementsSkill() {
		return new SchedulingSkill(REQUIREMENTS, Set.of(CONTEXT, RESOLVE), 8);
	}

	@Bean
	AgentSkill schedulingGenerationSkill() {
		return new SchedulingSkill(GENERATION, Set.of(GENERATION_VALIDATE, GENERATE, STATUS), 8);
	}

	@Bean
	AgentSkill schedulingConstraintsSkill() {
		return new SchedulingSkill(CONSTRAINTS, Set.of(CONTEXT, RESOLVE, CONSTRAINTS_VALIDATE), 8);
	}

	@Bean
	AgentSkill schedulingComparisonSkill() {
		return new SchedulingSkill(COMPARISON, Set.of(STATUS, COMPARE, PREVIEW), 8);
	}

	@Bean
	AgentTool<GenerationInput, Validation> schedulingConstraintValidator(
			AgentRunRepository runs, SchedulingAgentPlanValidator validator) {
		return new ValidationTool(CONSTRAINTS_VALIDATE, runs, validator);
	}

	@Bean
	AgentTool<GenerationInput, Validation> schedulingGenerationValidator(
			AgentRunRepository runs, SchedulingAgentPlanValidator validator) {
		return new ValidationTool(GENERATION_VALIDATE, runs, validator);
	}

	private record SchedulingSkill(String code, Set<String> allowedToolCodes, int maxToolCalls)
			implements AgentSkill {
		@Override
		public int schemaVersion() {
			return 1;
		}
	}

	@Component
	public static class ContextTool implements AgentTool<ContextInput, SchedulingAgentCatalogService.Page> {
		private final AgentRunRepository runs;
		private final SchedulingAgentCatalogService catalog;

		public ContextTool(AgentRunRepository runs, SchedulingAgentCatalogService catalog) {
			this.runs = runs;
			this.catalog = catalog;
		}

		@Override
		public String code() {
			return CONTEXT;
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
		public ToolResult<SchedulingAgentCatalogService.Page> execute(ToolContext context, ContextInput input) {
			AgentRun run = requireOwner(runs, context);
			if (!run.getSemesterCode().equals(input.semesterCode())) {
				throw new IllegalArgumentException("查询学期与 AI Run 不一致");
			}
			var result = catalog.lookup(context.actorUsername(), input.type(), input.semesterCode(),
					input.keyword(), input.page(), input.size());
			return ToolResult.success(result, input.semesterCode(), "已查询授权基础数据");
		}
	}

	@Component
	public static class EntityResolutionTool
			implements AgentTool<ResolveInput, SchedulingAgentCatalogService.Item> {
		private final AgentRunRepository runs;
		private final SchedulingAgentCatalogService catalog;

		public EntityResolutionTool(AgentRunRepository runs, SchedulingAgentCatalogService catalog) {
			this.runs = runs;
			this.catalog = catalog;
		}

		@Override
		public String code() {
			return RESOLVE;
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
		public ToolResult<SchedulingAgentCatalogService.Item> execute(
				ToolContext context, ResolveInput input) {
			AgentRun run = requireOwner(runs, context);
			if (!run.getSemesterCode().equals(input.semesterCode())) {
				throw new IllegalArgumentException("解析学期与 AI Run 不一致");
			}
			var item = catalog.resolve(context.actorUsername(), input.type(), input.semesterCode(),
					input.nameOrCode());
			return ToolResult.success(item, input.semesterCode(), "已验证唯一的业务实体");
		}
	}

	@Component
	public static class GenerationTool implements AgentTool<GenerationInput, String> {
		private final AgentRunRepository runs;
		private final ScheduleGenerationJobService jobs;
		private final SchedulingAgentPlanValidator plans;

		public GenerationTool(AgentRunRepository runs, ScheduleGenerationJobService jobs,
				SchedulingAgentPlanValidator plans) {
			this.runs = runs;
			this.jobs = jobs;
			this.plans = plans;
		}

		@Override
		public String code() {
			return GENERATE;
		}

		@Override
		public int inputSchemaVersion() {
			return 1;
		}

		@Override
		public ToolRiskLevel riskLevel() {
			return ToolRiskLevel.CONFIRMED_WRITE;
		}

		@Override
		public ToolResult<String> execute(ToolContext context, GenerationInput input) {
			AgentRun run = requireOwner(runs, context);
			if (!"CONFIRMED".equals(run.getStatus()) || input.planVersion() != run.getPlanVersion()) {
				throw new IllegalStateException("只有当前已确认版本可以提交排课");
			}
			var parameters = plans.fromConfirmedRun(run, context.actorUsername());
			ScheduleGenerationJob job = jobs.submit(parameters.command(), context.actorUsername(),
					run.getId(), parameters.constraints());
			return ToolResult.success(job.getId(), run.getSemesterCode(), "已提交本轮候选生成任务");
		}
	}

	private record ValidationTool(String code, AgentRunRepository runs,
			SchedulingAgentPlanValidator validator)
			implements AgentTool<GenerationInput, Validation> {
		@Override
		public int inputSchemaVersion() {
			return 1;
		}

		@Override
		public ToolRiskLevel riskLevel() {
			return ToolRiskLevel.READ_ONLY;
		}

		@Override
		public ToolResult<Validation> execute(ToolContext context, GenerationInput input) {
			AgentRun run = requireOwner(runs, context);
			if (input.planVersion() != run.getPlanVersion()
					|| !Set.of("READY_FOR_CONFIRMATION", "CONFIRMED").contains(run.getStatus())) {
				throw new IllegalStateException("当前计划版本尚未准备确认");
			}
			var parameters = "CONFIRMED".equals(run.getStatus())
					? validator.fromConfirmedRun(run, context.actorUsername())
					: validator.parameters(validator.fromDraftRun(run), context.actorUsername());
			return ToolResult.success(new Validation(parameters.command().candidateCount(),
					parameters.constraints().teacherSlots().size()
							+ parameters.constraints().offeringDurations().size()),
					run.getSemesterCode(), "已验证确认计划和动态规则");
		}
	}

	@Component
	public static class GenerationStatusTool implements AgentTool<Void, JobStatus> {
		private final AgentRunRepository runs;
		private final ScheduleGenerationJobService jobs;

		public GenerationStatusTool(AgentRunRepository runs, ScheduleGenerationJobService jobs) {
			this.runs = runs;
			this.jobs = jobs;
		}

		@Component
		public static class ComparisonTool implements AgentTool<CompareInput, List<ScheduleCandidateView>> {
			private final AgentRunRepository runs;
			private final SchedulingAgentCandidateService candidates;

			public ComparisonTool(AgentRunRepository runs, SchedulingAgentCandidateService candidates) {
				this.runs = runs;
				this.candidates = candidates;
			}

			@Override
			public String code() {
				return COMPARE;
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
			public ToolResult<List<ScheduleCandidateView>> execute(ToolContext context, CompareInput input) {
				AgentRun run = requireOwner(runs, context);
				var result = candidates.compare(run, context.actorUsername(), input.candidateIds());
				return ToolResult.success(result, run.getSemesterCode(), "已对比本轮候选实际指标");
			}
		}

		@Component
		public static class PreviewTool implements AgentTool<PreviewInput, ScheduleDiffView> {
			private final AgentRunRepository runs;
			private final SchedulingAgentCandidateService candidates;

			public PreviewTool(AgentRunRepository runs, SchedulingAgentCandidateService candidates) {
				this.runs = runs;
				this.candidates = candidates;
			}

			@Override
			public String code() {
				return PREVIEW;
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
			public ToolResult<ScheduleDiffView> execute(ToolContext context, PreviewInput input) {
				AgentRun run = requireOwner(runs, context);
				var result = candidates.preview(run, context.actorUsername(), input.candidateId());
				return ToolResult.success(result, run.getSemesterCode(), "已预览相对当前草稿的差异");
			}
		}

		@Override
		public String code() {
			return STATUS;
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
		public ToolResult<JobStatus> execute(ToolContext context, Void input) {
			AgentRun run = requireOwner(runs, context);
			if (run.getRelatedJobId() == null) {
				throw new IllegalStateException("当前 Run 尚未生成候选");
			}
			ScheduleGenerationJob job = jobs.require(run.getRelatedJobId());
			if (!run.getId().equals(job.getAgentRunId())
					|| !context.actorUsername().equals(job.getRequestedBy())) {
				throw new org.springframework.security.access.AccessDeniedException("任务不属于当前 Run");
			}
			return ToolResult.success(new JobStatus(job.getId(), job.getStatus(), job.getProgress(),
					job.getErrorMessage()), run.getSemesterCode(), "已查询当前 Run 的任务进度");
		}
	}

	private static AgentRun requireOwner(AgentRunRepository runs, ToolContext context) {
		AgentRun run = runs.findById(context.runId())
				.orElseThrow(() -> new IllegalArgumentException("AI Run 不存在"));
		if (!context.actorUsername().equals(run.getOwnerUsername())) {
			throw new org.springframework.security.access.AccessDeniedException("AI Run 不可访问");
		}
		return run;
	}

	public record ContextInput(String type, String semesterCode, String keyword, int page, int size) {
	}

	public record ResolveInput(String type, String semesterCode, String nameOrCode) {
	}

	public record GenerationInput(int planVersion) {
	}

	public record Validation(int candidateCount, int scopedRuleCount) {
	}

	public record JobStatus(String jobId, String status, Integer progress, String errorMessage) {
	}

	public record CompareInput(List<String> candidateIds) {
	}

	public record PreviewInput(String candidateId) {
	}
}
