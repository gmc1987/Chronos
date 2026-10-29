package com.chronos.education.operationagent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.education.operationagent.model.OperationAgentPlan;
import com.chronos.education.operationagent.model.OperationAgentStepRun;
import com.chronos.education.operationagent.service.OperationAgentService.PlanCommand;
import com.chronos.education.operationagent.service.OperationAgentService.StepCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 学期运营智能体的模型推理边界。模型只能生成计划草案和决策建议，工具白名单、
 * 参数校验、人工审批及正式数据写入仍由确定性的应用服务控制。
 */
@Service
public class OperationAgentLlmService {
	private static final int MAX_MODEL_OUTPUT = 32_000;
	private static final Pattern ADMISSION_YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?:\\s*级)?");
	private static final Pattern BATCH_LIMIT_PATTERN = Pattern.compile("每批(?:最多|不超过)?\\s*(\\d{1,4})\\s*(?:人|条)?");
	private static final Pattern SEMESTER_PATTERN = Pattern.compile("20\\d{2}-20\\d{2}-[12]");
	private final AiModelChatService models;
	private final OperationAgentToolCatalog tools;
	private final OperationAgentPlanningContextService planningContext;
	private final ObjectMapper mapper;

	public OperationAgentLlmService(
			AiModelChatService models,
			OperationAgentToolCatalog tools,
			OperationAgentPlanningContextService planningContext,
			ObjectMapper mapper) {
		this.models = models;
		this.tools = tools;
		this.planningContext = planningContext;
		this.mapper = mapper;
	}

	public DraftResult draft(DraftCommand command, Authentication authentication) {
		String request = require(command == null ? null : command.request(), "请描述希望智能体完成的工作");
		Map<String, Object> readiness = planningContext.inspect(command.semesterCode(), command.admissionYear());
		boolean candidateOnly = command.candidateOnly() == null || command.candidateOnly();
		String prompt = """
			你是学校学期运营智能体的规划器。请根据用户目标和当前业务准备度，转换为最小且完整的可审查执行计划草案。
			只能使用下方工具目录，不得编造工具、接口或数据库操作。写入型工具是否需要审批由平台最终强制决定。
			仅返回一个 JSON 对象，不要 Markdown。格式：
			{"intentSummary":"...","planName":"...","objective":"...","ruleText":"...","rationale":"...","steps":[
			{"stepName":"...","description":"...","toolCode":"...","successCriteria":"...","toolConfig":{}}
			]}
			要求：1. 只选择实现本次目标仍然需要的工具，不要固定生成全部开学阶段；2. 已由准备度快照证明完成的工作不要重复执行；
			3. 步骤按工具目录标注的业务阶段和依赖顺序排列，最多 24 个子步骤；4. 参数未知时仍保留对应步骤并让 toolConfig 缺少该字段，
			   由平台生成待补充项；不得猜测参数，也不得因为缺资料而省略用户明确要求的业务步骤或返回空 steps；5. 只读检查优先于写入；
			6. teaching.assignment.generate 的 toolConfig 在信息充足时包含 semesterCode 和 assignments 数组，
			   每项格式为 {"teacherNo":"工号","subjectCode":"学科编码","classCode":"行政班编码","weeklyLessons":2}；
			7. 新生名册文件 ID 只能使用调用方明确提供的 managedFileId，禁止猜测。
			8. 用户选择“先生成候选结果”时，本轮计划停在检查、导入校验、拟分班、教师分工候选或排课候选，不包含应用或发布正式课表。
			9. 每个文本字段保持简洁，description、successCriteria、rationale 各不超过 80 个中文字符；不要复述工具目录或准备度快照。
			工具目录：
			""" + toolPrompt() + "\n用户目标：" + request
				+ "\n当前业务准备度：" + writeJson(readiness)
				+ "\n先生成候选结果：" + candidateOnly
				+ optional("\n指定知识库 ID：", command.knowledgeBaseId())
				+ optional("\n指定学期：", command.semesterCode())
				+ optional("\n指定入学年份：", command.admissionYear() == null ? null : String.valueOf(command.admissionYear()))
				+ optional("\n可信新生名册 managedFileId：", command.managedFileId());
		JsonNode root;
		try {
			root = parseObject(callAs(authentication.getName(), () -> models.chat(null, prompt)));
		} catch (RuntimeException exception) {
			return fallbackDraft(command, readiness, candidateOnly, request, exception);
		}
		List<StepCommand> steps = new ArrayList<>();
		Set<String> authorized = new LinkedHashSet<>();
		JsonNode stepNodes = root.path("steps");
		if (!stepNodes.isArray() || stepNodes.isEmpty() || stepNodes.size() > 24) {
			throw new IllegalStateException("大模型返回的执行步骤数量无效");
		}
		int sourceOrder = 1;
		List<DraftStep> parsedSteps = new ArrayList<>();
		for (JsonNode node : stepNodes) {
			String toolCode = require(node.path("toolCode").asText(null), "大模型返回了缺少工具编码的步骤");
			if (authorized.contains(toolCode)) continue;
			OperationAgentToolCatalog.ToolDefinition definition = tools.require(toolCode);
			// 已有可用开课任务时，排课目标应直接复用现状。模型偶尔会额外生成
			// “教师分工/开课”步骤并要求用户重新提供映射，这会把本可自动完成的
			// 排课候选流程错误地停在待补充状态。只有用户明确要求重做这些基础数据时才保留。
			if (redundantPreparationStep(toolCode, request, readiness)) continue;
			Map<String, Object> config = objectMap(node.path("toolConfig"));
			if (command.semesterCode() != null && !command.semesterCode().isBlank()
					&& !config.containsKey("semesterCode")) {
				config.put("semesterCode", command.semesterCode().trim());
			}
			if (command.admissionYear() != null && toolCode.startsWith("admission.")) {
				config.putIfAbsent("admissionYear", command.admissionYear());
			}
			if ("admission.import.managed-file".equals(toolCode)
					&& command.managedFileId() != null && !command.managedFileId().isBlank()) {
				config.put("managedFileId", command.managedFileId().trim());
				config.putIfAbsent("dryRun", candidateOnly);
			}
			if (candidateOnly && definition.writeTool()) {
				config.put("dryRun", true);
			}
			if (candidateOnly && Set.of("scheduling.submit.best", "scheduling.review.submitted",
					"scheduling.apply.approved", "scheduling.publish").contains(toolCode)) {
				continue;
			}
			parsedSteps.add(new DraftStep(sourceOrder++, new StepCommand(
					"AI_STEP_PENDING",
					require(node.path("stepName").asText(null), "大模型返回了未命名步骤"),
					text(node, "description"),
					toolCode,
					definition.approvalRequired() && !candidateOnly,
					text(node, "successCriteria"),
					config)));
			authorized.add(toolCode);
		}
		// 上传名册是可信的结构化输入。即使模型遗漏导入步骤，也必须由平台在服务端补齐，
		// 不能再由前端临时插入，否则模型排序与业务依赖校验会产生两个事实来源。
		if (command.managedFileId() != null && !command.managedFileId().isBlank()
				&& !authorized.contains("admission.import.managed-file")) {
			if (parsedSteps.size() >= 24) {
				throw new IllegalStateException("执行计划已达到 24 个步骤，无法补入新生名册导入步骤");
			}
			Map<String, Object> config = new LinkedHashMap<>();
			config.put("managedFileId", command.managedFileId().trim());
			config.put("dryRun", candidateOnly);
			if (command.admissionYear() != null) config.put("admissionYear", command.admissionYear());
			parsedSteps.add(new DraftStep(sourceOrder, new StepCommand(
					"AI_STEP_PENDING", "接入新生名册", "读取受管 XLSX 文件并生成可复核的新生候选数据",
					"admission.import.managed-file", !candidateOnly,
					"名册格式和业务数据校验通过，生成新生候选数据且不直接写入正式档案", config)));
			authorized.add("admission.import.managed-file");
		}
		// A model may shorten an explicit end-to-end admission request to import
		// and class assignment only. Enforce the user's stated completion boundary
		// deterministically so account generation can never disappear from the plan.
		if (requiresCompletedAdmission(request)) {
			for (String toolCode : List.of(
					"admission.data.check", "admission.class.assign.batch",
					"admission.review.batch", "admission.enroll.batch",
					"student.account.provision.batch", "account.binding.check")) {
				if (authorized.contains(toolCode)) continue;
				OperationAgentToolCatalog.ToolDefinition definition = tools.require(toolCode);
				Map<String, Object> config = new LinkedHashMap<>();
				if (command.admissionYear() != null && toolCode.startsWith("admission.")) {
					config.put("admissionYear", command.admissionYear());
				}
				if (candidateOnly && definition.writeTool()) config.put("dryRun", true);
				parsedSteps.add(new DraftStep(++sourceOrder, new StepCommand(
						"AI_STEP_PENDING", definition.name(),
						"用户要求完成新生建档、账号生成与绑定，平台补齐不可省略的业务步骤。",
						toolCode, definition.approvalRequired() && !candidateOnly,
						"完成该阶段并保存可审计结果", config)));
				authorized.add(toolCode);
			}
		}
		parsedSteps.sort(Comparator
				.comparingInt((DraftStep value) -> tools.executionOrder(value.command().toolCode()))
				.thenComparingInt(DraftStep::sourceOrder));
		int order = 1;
		for (DraftStep value : parsedSteps) {
			StepCommand step = value.command();
			steps.add(new StepCommand("AI_STEP_" + order++, step.stepName(), step.description(),
					step.toolCode(), step.approvalRequired(), step.successCriteria(), step.toolConfig()));
		}
		List<RequiredInput> requiredInputs = requiredInputs(steps);
		List<String> warnings = requiredInputs.stream()
				.map(value -> value.label() + "：" + value.prompt()).toList();
		PlanCommand plan = new PlanCommand(
				require(root.path("planName").asText(null), "大模型未返回计划名称"),
				require(root.path("objective").asText(null), "大模型未返回计划目标"),
				require(root.path("ruleText").asText(null), "大模型未返回执行规则"),
				"MANUAL", null, LocalDate.now(), null,
				blank(command.knowledgeBaseId()), "OWNER", true,
				List.copyOf(authorized), steps);
		return new DraftResult(plan, text(root, "intentSummary"), text(root, "rationale"),
				readiness, requiredInputs, requiredInputs.isEmpty(), true, warnings);
	}

	/**
	 * DeepSeek is an advisory planner, so a transient provider timeout must not
	 * make the school workflow unusable. This deterministic planner selects only
	 * registered tools, preserves dependency order and keeps every write in
	 * candidate mode. The UI clearly identifies the fallback and lets the user
	 * inspect the same executable plan instead of receiving a dead-end error.
	 */
	private DraftResult fallbackDraft(
			DraftCommand command,
			Map<String, Object> readiness,
			boolean candidateOnly,
			String request,
			RuntimeException failure) {
		String goal = request.toLowerCase(java.util.Locale.ROOT);
		Set<String> selected = new LinkedHashSet<>();
		if (command.knowledgeBaseId() != null && !command.knowledgeBaseId().isBlank()) {
			selected.add("knowledge.policy.search");
		}
		if (command.managedFileId() != null && !command.managedFileId().isBlank()) {
			selected.add("admission.import.managed-file");
		}
		if (containsAny(goal, "新生", "名册", "录取", "入学", "学号", "学生账号", "分班")) {
			selected.add("admission.data.check");
		}
		if (containsAny(goal, "分班", "班级分配")) selected.add("admission.class.assign.batch");
		if (containsAny(goal, "资格复查", "新生审核", "复查")) selected.add("admission.review.batch");
		if (containsAny(goal, "报到", "建档", "学籍", "学号", "学生账号")) {
			selected.add("admission.review.batch");
			selected.add("admission.enroll.batch");
			selected.add("student.account.provision.batch");
			selected.add("account.binding.check");
		}
		if (containsAny(goal, "住宿", "宿舍", "床位")) {
			selected.add("admission.accommodation.resource.check");
			if (containsAny(goal, "维护", "导入", "补齐", "创建")) selected.add("admission.accommodation.resource.maintain");
			selected.add("admission.accommodation.check");
			selected.add("admission.accommodation.plan");
		}
		if (containsAny(goal, "教师入职", "教师账号", "教师档案")) selected.add("teacher.onboarding.batch");
		if (containsAny(goal, "教师分工", "任课", "教学任务")) selected.add("teaching.assignment.generate");
		if (containsAny(goal, "开课", "教学班", "选课")) selected.add("teaching.offering.generate");
		if (containsAny(goal, "排课", "课表")) {
			selected.add("teaching.readiness.check");
			selected.add("scheduling.generate.full");
			if (!candidateOnly) {
				selected.add("scheduling.submit.best");
				selected.add("scheduling.review.submitted");
				selected.add("scheduling.apply.approved");
				selected.add("scheduling.publish");
			}
		}
		if (containsAny(goal, "期末", "成绩收口")) selected.addAll(List.of(
				"term.end.check", "term.end.exam.check", "term.end.grade.check", "term.end.archive.check"));
		if (containsAny(goal, "考试收口", "考试安排", "监考")) selected.add("term.end.exam.check");
		if (containsAny(goal, "成绩发布", "成绩归档")) selected.add("term.end.grade.check");
		if (containsAny(goal, "归档", "学期交接")) selected.add("term.end.archive.check");
		if (containsAny(goal, "毕业")) selected.add("term.graduation.prepare");
		// 用户明确要求完成新生建档、账号生成或账号绑定时，无论 DeepSeek
		// 如何失败，安全规划器都必须补齐整条依赖链。不能仅依赖“分班”一词，
		// 否则“行政班分配”等同义表达会遗漏阶段 3，后续报到建档必然无班可用。
		if (requiresCompletedAdmission(request)) {
			selected.addAll(List.of(
					"admission.import.managed-file", "admission.data.check",
					"admission.class.assign.batch", "admission.review.batch",
					"admission.enroll.batch", "student.account.provision.batch",
					"account.binding.check"));
		}
		if (selected.isEmpty()) selected.add("semester.context.check");

		List<StepCommand> steps = new ArrayList<>();
		List<String> authorized = selected.stream()
				.sorted(Comparator.comparingInt(tools::executionOrder)).toList();
		int order = 1;
		for (String toolCode : authorized) {
			OperationAgentToolCatalog.ToolDefinition definition = tools.require(toolCode);
			Map<String, Object> config = new LinkedHashMap<>();
			if (command.semesterCode() != null && !command.semesterCode().isBlank()) {
				config.put("semesterCode", command.semesterCode().trim());
			}
			if (command.admissionYear() != null && toolCode.startsWith("admission.")) {
				config.put("admissionYear", command.admissionYear());
			}
			if ("admission.import.managed-file".equals(toolCode)) {
				if (command.managedFileId() != null && !command.managedFileId().isBlank()) {
					config.put("managedFileId", command.managedFileId().trim());
				}
			}
			if (candidateOnly && definition.writeTool()) config.put("dryRun", true);
			steps.add(new StepCommand("SAFE_STEP_" + order++, definition.name(),
					"平台根据用户目标和业务依赖选择该受控工具。", toolCode,
					definition.approvalRequired() && !candidateOnly,
					"业务工具返回可审计结果，异常时停在安全检查点。", config));
		}
		List<RequiredInput> requiredInputs = requiredInputs(steps);
		List<String> warnings = new ArrayList<>();
		warnings.add("DeepSeek 本次调用超时或不可用，平台已切换到受控业务规划器；执行步骤仍需用户确认");
		warnings.addAll(requiredInputs.stream().map(value -> value.label() + "：" + value.prompt()).toList());
		PlanCommand plan = new PlanCommand(
				"智能体安全执行计划", request,
				"仅调用已注册业务工具；遵守阶段依赖；候选结果确认后才允许正式写入。",
				"MANUAL", null, LocalDate.now(), null, blank(command.knowledgeBaseId()), "OWNER", true,
				authorized, steps);
		String cause = failure.getClass().getSimpleName();
		return new DraftResult(plan,
				"已识别用户目标，准备通过受控业务工具实际处理：" + request,
				"DeepSeek 调用未完成（" + cause + "），平台按工具目录和业务依赖生成了可执行的安全降级计划。",
				readiness, requiredInputs, requiredInputs.isEmpty(), false, List.copyOf(warnings));
	}

	private boolean containsAny(String source, String... values) {
		for (String value : values) if (source.contains(value)) return true;
		return false;
	}

	private boolean redundantPreparationStep(String toolCode, String request, Map<String, Object> readiness) {
		if (number(readiness.get("courseOfferingCount")) <= 0) return false;
		String goal = request == null ? "" : request.toLowerCase(java.util.Locale.ROOT);
		if ("teaching.assignment.generate".equals(toolCode)) {
			return !containsAny(goal, "教师分工", "任课安排", "教学任务", "重新分工", "重做分工");
		}
		if ("teaching.offering.generate".equals(toolCode)) {
			return !containsAny(goal, "开课", "教学班", "选课", "重新开课", "重建教学班");
		}
		return false;
	}

	private long number(Object value) {
		if (value instanceof Number number) return number.longValue();
		try {
			return value == null ? 0L : Long.parseLong(String.valueOf(value));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private boolean requiresCompletedAdmission(String request) {
		String goal = request == null ? "" : request.toLowerCase(java.util.Locale.ROOT);
		boolean admissionContext = containsAny(goal, "新生", "名册", "录取", "入学");
		return admissionContext && containsAny(goal,
				"完整流程", "全流程", "账号生成", "生成账号", "学生账号", "开通账号", "账号绑定", "绑定账号", "建档");
	}

	private List<RequiredInput> requiredInputs(List<StepCommand> steps) {
		List<RequiredInput> result = new ArrayList<>();
		for (StepCommand step : steps) {
			for (OperationAgentToolCatalog.InputRequirement requirement
					: tools.missingInputs(step.toolCode(), step.toolConfig())) {
				result.add(new RequiredInput(step.stepCode(), step.stepName(), step.toolCode(),
						requirement.key(), requirement.label(), requirement.prompt()));
			}
		}
		return List.copyOf(result);
	}

	public Map<String, Object> interpretCondition(String conditionText, String actor) {
		String source = require(conditionText, "附加条件不能为空");
		return controlledCondition(source,
				"平台已即时提取入学年份、学期、批量上限和禁止写入等可执行条件。");
	}

	private Map<String, Object> controlledCondition(String source, String rationale) {
		Map<String, Object> result = new LinkedHashMap<>();
		Matcher year = ADMISSION_YEAR_PATTERN.matcher(source);
		if (year.find()) result.put("admissionYear", Integer.parseInt(year.group(1)));

		Matcher batch = BATCH_LIMIT_PATTERN.matcher(source);
		if (batch.find()) {
			result.put("batchLimit", Math.max(1, Math.min(500, Integer.parseInt(batch.group(1)))));
		}

		Matcher semester = SEMESTER_PATTERN.matcher(source);
		if (semester.find()) result.put("semesterCode", semester.group());

		boolean blockWriteTools = containsAny(source,
				"不要写入", "禁止写入", "禁止正式", "不写正式", "不要落库", "只读");
		result.put("blockWriteTools", blockWriteTools);
		result.put("llmRationale", rationale);
		result.put("sourceText", source);
		return result;
	}

	public StepDecision assessStep(
			OperationAgentPlan plan,
			OperationAgentStepRun step,
			OperationAgentToolCatalog.ToolDefinition definition,
			Map<String, Object> effectiveConfig,
			String actor) {
		String prompt = """
			你是学校学期运营智能体的步骤决策器。判断当前受控工具是否可继续，或是否应增加人工确认。
			仅返回 JSON：{"decision":"PROCEED或REQUIRE_HUMAN","rationale":"...","nextAction":"..."}。
			不得建议绕过权限、审批、业务校验或直接操作数据库。信息足够且平台已有审批时选择 PROCEED；
			存在目标歧义、正式数据风险或需人工选择时选择 REQUIRE_HUMAN。
			dryRun=true 表示只生成候选或预览，不写正式业务数据，不应仅因工具原始风险等级要求人工审批。
			"""
				+ "\n计划目标：" + plan.getObjective()
				+ "\n执行规则：" + plan.getRuleText()
				+ "\n当前步骤：" + step.getStepName()
				+ "\n工具：" + definition.code() + " / " + definition.name()
				+ "\n风险：" + definition.riskLevel()
				+ "\n平台预设人工审批：" + definition.approvalRequired()
				+ "\n当前为候选预览：" + Boolean.TRUE.equals(effectiveConfig.get("dryRun"));
		JsonNode root;
		try {
			root = parseObject(callAs(actor, () -> models.chat(null, prompt)));
		} catch (RuntimeException exception) {
			boolean candidatePreview = Boolean.TRUE.equals(effectiveConfig.get("dryRun"));
			boolean humanRequired = definition.approvalRequired() && !candidatePreview;
			return new StepDecision(humanRequired ? "REQUIRE_HUMAN" : "PROCEED",
					"DeepSeek 步骤判断未完成，平台按工具风险、候选模式和预设审批规则执行安全降级。",
					humanRequired ? "请审核后继续执行正式数据写入" : "按受控工具配置继续执行");
		}
		String decision = root.path("decision").asText("").trim().toUpperCase();
		if (!Set.of("PROCEED", "REQUIRE_HUMAN").contains(decision)) {
			throw new IllegalStateException("大模型返回了无效的步骤决策");
		}
		return new StepDecision(decision, require(text(root, "rationale"), "大模型未说明步骤决策理由"),
				text(root, "nextAction"));
	}

	public String analyzeFailure(OperationAgentPlan plan, OperationAgentStepRun step, Throwable failure, String actor) {
		String prompt = """
			你是学校学期运营智能体的故障分析器。根据失败信息给出简短、可执行且不破坏数据的恢复建议。
			不要建议直接改数据库或绕过审批。仅返回纯文本，最多 300 个中文字符。
			""" + "\n计划：" + plan.getObjective() + "\n步骤：" + step.getStepName()
				+ "\n错误：" + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
		try {
			return callAs(actor, () -> models.chat(null, prompt)).trim();
		} catch (RuntimeException ignored) {
			return "模型未能生成恢复建议，请依据业务工具错误和预设恢复策略处理。";
		}
	}

	private String toolPrompt() {
		StringBuilder result = new StringBuilder();
		for (OperationAgentToolCatalog.ToolDefinition tool : tools.all()) {
			result.append("- ").append(tool.code()).append(" | ").append(tool.name())
					.append(" | risk=").append(tool.riskLevel())
					.append(" | write=").append(tool.writeTool())
					.append(" | stage=").append(tool.businessStageOrder() == null ? "TERM_END" : tool.businessStageOrder())
					.append('/').append(tool.businessStageName() == null ? "期末收口" : tool.businessStageName())
					.append(" | required=").append(tool.inputRequirements().stream()
							.map(OperationAgentToolCatalog.InputRequirement::key).toList())
					.append('\n');
		}
		return result.toString();
	}

	private JsonNode parseObject(String response) {
		if (response == null || response.isBlank() || response.length() > MAX_MODEL_OUTPUT) {
			throw new IllegalStateException("大模型返回内容为空或超过长度限制");
		}
		String value = response.trim();
		int start = value.indexOf('{');
		int end = value.lastIndexOf('}');
		if (start < 0 || end <= start) throw new IllegalStateException("大模型未返回有效 JSON");
		try {
			JsonNode root = mapper.readTree(value.substring(start, end + 1));
			if (root == null || !root.isObject()) throw new IllegalStateException("大模型返回格式无效");
			return root;
		} catch (Exception exception) {
			throw new IllegalStateException("大模型返回的 JSON 无法解析", exception);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> objectMap(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) return new LinkedHashMap<>();
		if (!node.isObject()) throw new IllegalStateException("大模型返回的工具参数不是 JSON 对象");
		return mapper.convertValue(node, LinkedHashMap.class);
	}

	private <T> T callAs(String actor, Supplier<T> call) {
		SecurityContext previous = SecurityContextHolder.getContext();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(
				actor == null || actor.isBlank() ? "SYSTEM" : actor, "", List.of()));
		SecurityContextHolder.setContext(context);
		try {
			return call.get();
		} finally {
			SecurityContextHolder.setContext(previous);
		}
	}

	private String require(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalStateException(message);
		return value.trim();
	}

	private String text(JsonNode node, String field) {
		String value = node.path(field).asText("").trim();
		return value.length() <= 2000 ? value : value.substring(0, 2000);
	}

	private String optional(String prefix, String value) {
		return value == null || value.isBlank() ? "" : prefix + value.trim();
	}

	private String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String writeJson(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException("业务准备度快照无法序列化", exception);
		}
	}

	private record DraftStep(int sourceOrder, StepCommand command) {}
	public record DraftCommand(
			String request,
			String knowledgeBaseId,
			String semesterCode,
			Integer admissionYear,
			String managedFileId,
			Boolean candidateOnly) {}
	public record RequiredInput(
			String stepCode,
			String stepName,
			String toolCode,
			String key,
			String label,
			String prompt) {}
	public record DraftResult(
			PlanCommand plan,
			String intentSummary,
			String rationale,
			Map<String, Object> readiness,
			List<RequiredInput> requiredInputs,
			boolean readyToSave,
			boolean modelGenerated,
			List<String> warnings) {}
	public record StepDecision(String decision, String rationale, String nextAction) {}
}
