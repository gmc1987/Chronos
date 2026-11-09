package com.chronos.education.operationagent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	@org.springframework.beans.factory.annotation.Value("${chronos.operation-agent.model-timeout-seconds:180}")
	private int interactiveModelTimeoutSeconds = 180;
	private static final Pattern ADMISSION_YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?:\\s*级)?");
	private static final Pattern BATCH_LIMIT_PATTERN = Pattern.compile("每批(?:最多|不超过)?\\s*(\\d{1,4})\\s*(?:人|条)?");
	private static final Pattern SEMESTER_PATTERN = Pattern.compile("20\\d{2}-20\\d{2}-[12]");
	private static final Pattern TERM_START_DATE_PATTERN = Pattern.compile(
			"(?:开始日期|开学日期|学期开始)\\s*[:：为是]?\\s*(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})");
	private static final Pattern TERM_END_DATE_PATTERN = Pattern.compile(
			"(?:结束日期|放假日期|学期结束)\\s*[:：为是]?\\s*(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})");
	private static final Pattern TERM_DATE_RANGE_PATTERN = Pattern.compile(
			"(?:从)?\\s*(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})\\s*(?:到|至|~|—|-)\\s*(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})");
	private static final Pattern TERM_WEEK_COUNT_PATTERN = Pattern.compile(
			"(?:(?:教学)?周数\\s*[:：为是]?\\s*(\\d{1,2})|(?:共|设置为|设为)?\\s*(\\d{1,2})\\s*(?:个)?(?:教学)?周)");
	private static final Pattern TERM_COMPACT_INPUT_PATTERN = Pattern.compile(
			"(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})\\s*[;；,，\\s]+(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})\\s*[;；,，\\s]+(\\d{1,2})");
	private static final Pattern TERM_BARE_WEEK_FOLLOW_UP_PATTERN = Pattern.compile(
			"(?:^|\\n用户补充：)\\s*(\\d{1,2})\\s*$");
	private static final Pattern TERM_CREATION_PATTERN = Pattern.compile(
			"(?:创建|新建|新增|建立|初始化|开设)(?:一个|新的|新)?\\s*(?:(?:20\\d{2}\\s*[-—至]\\s*20\\d{2}\\s*(?:学年)?\\s*(?:[-—]\\s*)?第?[一二12]学期)|(?:20\\d{2}\\s*(?:年度|学年)\\s*第?[一二12]学期)|学年学期|学期数据|新学期|学期)|"
					+ "(?:学年学期|学期数据|新学期|学期).{0,8}(?:创建|新建|新增|建立|初始化|开设)");
	private final AiModelChatService models;
	private final OperationAgentToolCatalog tools;
	private final OperationAgentSkillCatalog skills;
	private final OperationAgentManagedFileService managedFiles;
	private final OperationAgentPlanningContextService planningContext;
	private final ObjectMapper mapper;

	public OperationAgentLlmService(
			AiModelChatService models,
			OperationAgentToolCatalog tools,
			OperationAgentSkillCatalog skills,
			OperationAgentManagedFileService managedFiles,
			OperationAgentPlanningContextService planningContext,
			ObjectMapper mapper) {
		this.models = models;
		this.tools = tools;
		this.skills = skills;
		this.managedFiles = managedFiles;
		this.planningContext = planningContext;
		this.mapper = mapper;
	}

	/** 每轮首先由模型解释语义；业务规则只校验结构化结果，不解释用户原文。 */
	public JsonNode recognizeConversation(String conversation, Authentication authentication) {
		String prompt = """
			你是学校运营智能体的意图识别器。根据完整多轮用户消息输出一个 JSON 对象。
			格式：{"requiresSemester":true,"semesterCode":null,"createTerm":false,
			"confirmed":false,"cancelled":false,"needsClarification":false,"question":null,"skillCodes":[],"toolConfigs":{}}
			从下方 Skill 目录选择所有目标。学期维护使用 academic.term-setup。
			需要使用已有流程时，另返回 workflowReference，填用户提供的准确流程名称、编号或编码；不要猜测。
			semesterCode 是校内编码，例如 2026-2027-2；未指定时返回 null，由系统使用当前学期。
			用户已明确学年和学期序号时，必须同时填写顶层semesterCode和创建参数localTermCode，不能只填写toolConfigs。
			2026年度第二学期按2026-2027-2理解；存在歧义不要猜测。
			toolConfigs 仅为学期维护返回用户要求的工具键 academic.term.create、academic.calendar.prepare、academic.bell.configure。
			创建工具参数为 localTermCode,academicYear,termNo,termName,startDate,endDate,weekCount；
			作息工具参数为 campusId、scheduleName，以及 periods 或 periodRules。只有用户逐节提供完整时间时才输出 periods（每节包含 periodNo,periodName,daySegment,startTime,endTime；daySegment为MORNING/AFTERNOON/EVENING）。
			用户提供节数、起止时间、课长、课间或午休规则时，使用 periodRules，字段为 periodCount、morningCount、startTime、endTime、periodMinutes、breakMinutes、lunchMinutes（午休是最后一节上午课到第一节下午课的完整间隔）。只填已明确的字段，平台计算节次；未知的可选字段省略。方案编码由平台生成；方案名称缺省时平台自动命名，不要追问。
			campusId也接受用户明确给出的校区编码或准确名称，由平台查询解析；不要因不知道UUID而反复追问已提供的校区。只说“主校区”但无法确定具体名称时才询问。
			保留早先已给出的字段，用户更正时以最新消息为准。“是的”承接之前确认，单独数字承接缺失周数。
			缺失字段省略，禁止编造日期、机构、校区ID或节次。当前消息取消时 cancelled=true。
			识别到教学日历和作息等多个目标时必须全部保留。
			仅维护已有学期的作息或校历时，createTerm=false，禁止返回 academic.term.create；补充学期编码只是指定归属，不代表要创建学期。已有学期的日历日期由平台读取，不要追问起止日期。
			只要业务目标明确，即使缺少日期或节次，也返回对应的工具键及已知参数，交由平台追问缺项。
			用户要求创建作息且没有指定节次规则时，也返回作息工具键，平台会生成供确认的标准作息候选。
			平台的候选模式通过同一个业务工具的dryRun=true实现，不需要独立“候选工具”。
			教职工文件导入、账号开通、教师建档等技能可使用候选模式；用户要求先预览不是目标不明确，不得据此追问是否改为正式写入。
			平台在生成候选后负责正式写入、提交和发布的确认。用户已明确业务目标并补齐资料时，直接进入候选规划，needsClarification=false，不要在识别阶段再询问“是否提交或送审”。
			例如首轮要求成绩更正并在本人确认后送教务审核，第二轮补齐更正原因，应保留原成绩编号、目标分数和送审目标，直接进入规划，仍由平台在候选结果后要求确认。
			用户明确要求先等补充、不开始执行，或尚无法确定业务目标时，needsClarification=true，question给出具体追问；不要擅自启动任务。
			只输出JSON。Skill目录：
			""" + skillPrompt() + "\n多轮消息：\n" + conversation;
		JsonNode intent;
		try {
			intent = parseObject(callModelAs(authentication.getName(), () -> models.chat(null, prompt)));
		} catch (RuntimeException exception) {
			throw new IllegalStateException("大模型意图识别失败或超时，请重试；本轮未执行业务操作", exception);
		}
		if (!intent.path("skillCodes").isArray() || !intent.path("toolConfigs").isObject()
				|| !intent.path("requiresSemester").isBoolean()) {
			throw new IllegalStateException("模型意图结构无效，请重试；本轮未执行业务操作");
		}
		for (JsonNode code : intent.path("skillCodes")) {
			if (skills.all().stream().noneMatch(skill -> skill.code().equals(code.asText()))) {
				throw new IllegalStateException("模型选择了未注册的业务能力");
			}
		}
		// 模型可能仅在工具参数中给出学期。只在结构化字段之间归一，绝不重新匹配用户原文。
		if (intent.path("semesterCode").asText("").isBlank()) {
			JsonNode term = intent.path("toolConfigs").path("academic.term.create");
			String code = term.path("localTermCode").asText("");
			if (code.isBlank() && !term.path("academicYear").asText("").isBlank()
					&& term.path("termNo").canConvertToInt()) {
				code = term.path("academicYear").asText() + "-" + term.path("termNo").asInt();
			}
			if (!code.isBlank() && intent instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
				object.put("semesterCode", code);
			}
		}
		return intent;
	}

	public DraftResult academicIntentDraft(JsonNode intent, DraftCommand command, String termId, boolean createTerm) {
		List<StepCommand> steps = new ArrayList<>();
		Set<String> authorized = new LinkedHashSet<>();
		List<RequiredInput> corrections = new ArrayList<>();
		for (String code : List.of("academic.term.create", "academic.calendar.prepare", "academic.bell.configure")) {
			if (code.equals("academic.term.create") ? !createTerm : !intent.path("toolConfigs").has(code)) continue;
			var definition = tools.require(code);
			Map<String, Object> config = new LinkedHashMap<>(objectMap(intent.path("toolConfigs").path(code)));
			if (code.equals("academic.calendar.prepare")) {
				JsonNode termInput = intent.path("toolConfigs").path("academic.term.create");
				for (String field : List.of("startDate", "endDate")) {
					if (termInput.hasNonNull(field)) config.putIfAbsent(field, termInput.path(field).asText());
				}
			}
			if (code.equals("academic.term.create")) {
				String localCode = localTermCode(command.semesterCode());
				// 这里只规范模型已识别的编码，未读取或匹配用户自然语言。
				if (localCode != null && localCode.matches("20\\d{2}-20\\d{2}-[12]")) {
					String year = localCode.substring(0, 9);
					int termNo = Integer.parseInt(localCode.substring(10));
					config.put("localTermCode", localCode);
					config.put("academicYear", year);
					config.put("termNo", termNo);
					config.putIfAbsent("termName", year + "学年第" + (termNo == 1 ? "一" : "二") + "学期");
				}
			}
			String stepCode = "AI_STEP_" + (steps.size() + 1);
			if (code.equals("academic.bell.configure")) {
				config.putIfAbsent("scheduleName", localTermCode(command.semesterCode()) + " 校区作息");
				config.remove("scheduleCode");
				if (!(config.get("periods") instanceof List<?> periods) || periods.isEmpty()) {
					try {
						config.put("periods", OperationAgentBellPeriods.generate(
								objectMap(intent.path("toolConfigs").path(code).path("periodRules"))));
						config.put("generatedPeriods", true);
					} catch (IllegalArgumentException exception) {
						corrections.add(new RequiredInput(stepCode, definition.name(), code,
								"periods", "节次规则", exception.getMessage()));
					}
				}
			}
			config.put("organizationId", command.organizationId());
			config.put("dryRun", !Boolean.FALSE.equals(command.candidateOnly()));
			if (!code.equals("academic.term.create")) {
				config.put("termId", createTerm && authorized.contains("academic.term.create")
						? "${academic.term.create.termId}" : termId);
			}
			authorized.add(code);
			steps.add(new StepCommand(stepCode, definition.name(),
					"按模型识别的业务目标执行", code, false, "业务校验通过并保存结果", config));
		}
		if (steps.isEmpty()) throw new IllegalStateException("模型未返回学期维护业务目标，请补充说明");
		List<RequiredInput> missing = new ArrayList<>(requiredInputs(steps));
		if (!corrections.isEmpty()) {
			missing.removeIf(input -> corrections.stream().anyMatch(correction ->
					correction.stepCode().equals(input.stepCode()) && correction.key().equals(input.key())));
			missing.addAll(corrections);
		}
		// objective 是业务摘要（数据库上限1000字），完整多轮输入和结构化意图放入text审计字段。
		String objective = "学期 " + intent.path("semesterCode").asText(command.semesterCode()) + "："
				+ steps.stream().map(StepCommand::stepName).collect(Collectors.joining("、"));
		PlanCommand plan = new PlanCommand("学期维护", objective,
				"正式写入前按业务策略确认。\n用户目标及模型意图：\n" + command.request(),
				"MANUAL", null, LocalDate.now(), null, command.knowledgeBaseId(), "OWNER", true,
				List.copyOf(authorized), steps);
		return new DraftResult(plan, "已通过大模型识别多轮业务目标", "按注册Skill编排业务工具",
				Map.of(), skills.all().stream().filter(skill -> "academic.term-setup".equals(skill.code())).toList(),
				missing, missing.isEmpty(), true, List.of());
	}

	public void validateIntentTools(JsonNode intent, DraftResult draft) {
		Set<String> allowed = allowedIntentTools(intent);
		for (String tool : draft.plan().authorizedTools()) {
			if (!allowed.contains(tool)) throw new IllegalStateException("规划工具不属于本轮识别的业务能力：" + tool);
		}
	}

	private Set<String> allowedIntentTools(JsonNode intent) {
		Set<String> allowed = new LinkedHashSet<>(List.of("semester.context.check", "knowledge.policy.search"));
		for (JsonNode code : intent.path("skillCodes")) {
			skills.all().stream().filter(skill -> skill.code().equals(code.asText()))
					.forEach(skill -> allowed.addAll(skill.toolCodes()));
		}
		return allowed;
	}

	private static final Set<String> FORMAL_ONLY_TOOLS = Set.of(
			"scheduling.submit.best", "scheduling.review.submitted", "scheduling.apply.approved", "scheduling.publish",
			"workflow.task.complete", "exam.plan.publish", "grade.submit", "grade.review", "grade.publish",
			"grade.change.review");

	public DraftResult draft(DraftCommand command, Authentication authentication) {
		return draft(command, authentication, null);
	}

	public DraftResult draft(DraftCommand command, Authentication authentication, JsonNode intent) {
		String request = require(command == null ? null : command.request(), "请描述希望智能体完成的工作");
		List<ResolvedAttachment> attachments = resolveAttachments(command, authentication);
		String organizationId = blank(command.organizationId());
		Map<String, Object> readiness = organizationId == null
				? planningContext.inspect(command.semesterCode(), command.admissionYear())
				: planningContext.inspect(command.semesterCode(), command.admissionYear(), organizationId);
		boolean candidateOnly = command.candidateOnly() == null || command.candidateOnly();
		String workflowContext = "";
		if (intent != null && java.util.stream.StreamSupport.stream(intent.path("skillCodes").spliterator(), false)
				.anyMatch(code -> "workflow.dynamic-form-operation".equals(code.asText()))) {
			workflowContext = "\n当前账号可发起的流程及已定位的表单字段：" + writeJson(
					planningContext.availableWorkflowSchemas(authentication.getName(),
							blank(intent.path("workflowReference").asText(null))));
		}
		String prompt = """
			你是学校学期运营智能体的规划器。请根据用户目标和当前业务准备度，转换为最小且完整的可审查执行计划草案。
			只能使用下方工具目录，不得编造工具、接口或数据库操作。写入型工具是否需要审批由平台最终强制决定。
			仅返回一个 JSON 对象，不要 Markdown。格式：
			{"intentSummary":"...","planName":"...","objective":"...","ruleText":"...","rationale":"...","requiredTools":[],"steps":[
			{"stepName":"...","description":"...","toolCode":"...","successCriteria":"...","toolConfig":{}}
			]}
			要求：1. 先识别一个或多个业务 Skill，再选择实现目标仍然需要的原子 Tool；不要固定生成全部开学阶段；2. 已由准备度快照证明完成的工作不要重复执行；
			3. 步骤按工具目录标注的业务阶段和依赖顺序排列，最多 24 个子步骤；4. 参数未知时仍保留对应步骤并让 toolConfig 缺少该字段，
			   由平台生成待补充项；不得猜测参数，也不得因为缺资料而省略用户明确要求的业务步骤或返回空 steps；5. 只读检查优先于写入；
			6. teaching.assignment.generate 的 toolConfig 在信息充足时包含 semesterCode 和 assignments 数组，
			   每项格式为 {"teacherNo":"工号","subjectCode":"学科编码","classCode":"行政班编码","weeklyLessons":2}；
			7. 用户要求创建、新建或初始化学年学期时，必须使用 academic.term.create，不得用 semester.context.check 代替；
			8. 业务文件的类型和工具由平台服务端识别；只能使用可信附件清单中提供的文件 ID 和 suggestedToolCode，禁止猜测或改变文件用途。
			9. 用户选择“先生成候选结果”时仍保留其明确要求的完整业务链，例如导入、分班、复查、建档、账号与绑定核验；平台对写工具强制dryRun，不得因预览而省略后续目标。排课候选不得包含应用或发布正式课表。
			10. 后续步骤需要引用前一步输出 ID 时，使用 ${工具编码.字段名}，例如 ${exam.plan.upsert.planId}；平台会在执行时解析，禁止猜测新建数据 ID。
			11. 每个文本字段保持简洁，description、successCriteria、rationale 各不超过 80 个中文字符；不要复述工具目录或准备度快照。
			12. 保留多轮已经提供的所有目标和参数，最新更正覆盖旧值，简短数字或“是的”必须结合历史理解。
			13. 用户要求导入的每个附件必须安排对应导入工具；附件仅作为参考时不要导入。平台不会按关键词替你补计划。
			14. 用户要求完整入学及账号办理时保留分班、复查、建档、账号生成和绑定依赖；不能只导入就声称完成。
			15. 用户只要求排课且准备度已有教学任务时直接复用，不要求重建教师分工和开课。
			16. requiredTools只列本轮允许执行且必须具备的工具，steps必须覆盖它们。“先不提交复核”“不发布”“暂不更正”排除对应动作；不得将历史计划中的已撤回动作当成本轮目标。
			17. 考试冲突检查在候选模式也保留；未落库的新计划只标记待实际核验。新建场次/考场引用依次使用 ${exam.sessions.create.sessions.0.sessionId}、${exam.rooms.create.rooms.0.roomId}，按实际数组顺序引用。
			18. 考试计划参数放在 toolConfig.plan，字段为 semesterCode、planName、examType、startDate、endDate、baseInvigilators。场次使用 sessions 数组，字段为 subjectId、examDate、startTime、endTime、offeringIds。
			考试考场的 toolConfig.rooms 每项必须包含 sessionId、classroomId、requiredInvigilators；考生的 toolConfig.assignments 每项包含 roomId 与 studentIds 或 administrativeClassIds。
			19. 成绩方案放在 toolConfig.scheme，字段为 offeringId、name、totalScore、passScore、components，项目含 code、name、sourceType、weight、maxScore、sortOrder。成绩册放在 toolConfig.gradebook，字段为 offeringId、schemeId。录入放在 toolConfig.itemsCommand，字段为 items 和 rowVersion；每条含 studentId、componentId、rawScore。引用同计划新成绩项目时用 ${grade.scheme.upsert.componentIds.项目编码}，不要询问未创建对象的ID。
			成绩册只能使用已发布方案；若用户要求使用本次新建方案创建成绩册，方案步骤需在 toolConfig 顶层设置 publish=true。若用户要求只保存草稿，不要安排使用该方案创建成绩册。
			20. 发起已有流程时用 toolConfig.flowId 及 toolConfig.formData 对象。若提供可发起流程目录，使用其中的 id；否则仅使用用户明确提供的定义编号。formData 的键用表单定义的 fieldKey，将用户已给值映射进去，不要求用户提供JSON或内部键。
			21. “提交审核”“交独立审核”指发起流程后进入工作流待办，由实际审核人办理。只有用户明确要求办理已有待办时才使用 workflow.task.complete；workflow.instance.start 不返回 taskId。
			22. 宿舍资源维护使用 toolConfig.resources 扁平数组，每间房一行，字段为 buildingCode、buildingName、genderPolicy、roomNo、floorNo、bedCount。不得嵌套 rooms 或使用 roomCode。genderPolicy 使用 MIXED（混住）、MALE（男生）或 FEMALE（女生）。
			只能从本轮原子 Tool 目录选择步骤。业务审批资格属于对应业务，不使用 IAM 角色核查来判断任务办理资格。
			只检查能否审核成绩册时，使用 grade.review.check，填写 gradebookId 和 taskId；不要安排实际审批。
			发起成绩更正使用 grade.change.request。toolConfig 顶层填写 gradebookId，change 对象填写 courseGradeId（原成绩记录编号）、afterScore（更正后的分数）和 reason（更正原因）。发起并交教务审核只安排提交，不代办独立审核。
			""" + (intent == null ? "\n业务 Skill 目录：\n" + skillPrompt() : "")
				+ "\n原子 Tool 目录：\n" + toolPrompt(intent == null ? null : allowedIntentTools(intent))
				+ "\n用户目标：" + request
				+ "\n当前业务准备度：" + writeJson(readiness)
				+ workflowContext
				+ "\n服务端已识别的可信附件：" + writeJson(attachments)
				+ "\n先生成候选结果：" + candidateOnly
				+ optional("\n指定知识库 ID：", command.knowledgeBaseId())
				+ optional("\n指定学期：", command.semesterCode())
				+ optional("\n指定目标机构：", command.organizationId())
				+ optional("\n指定入学年份：", command.admissionYear() == null ? null : String.valueOf(command.admissionYear()));
		JsonNode root;
		try {
			root = parseObject(callModelAs(authentication.getName(), () -> models.chat(null, prompt)));
		} catch (RuntimeException exception) {
			return fallbackDraft(command, attachments, readiness, candidateOnly, request, exception);
		}
		List<StepCommand> steps = new ArrayList<>();
		Set<String> authorized = new LinkedHashSet<>();
		JsonNode stepNodes = root.path("steps");
		if (!stepNodes.isArray() || stepNodes.isEmpty() || stepNodes.size() > 24) {
			throw new IllegalStateException("大模型返回的执行步骤数量无效");
		}
		int sourceOrder = 1;
		List<DraftStep> parsedSteps = new ArrayList<>();
		Set<String> plannedTools = new LinkedHashSet<>();
		boolean omittedInvalidWorkflowApproval = false;
		stepNodes.forEach(step -> plannedTools.add(step.path("toolCode").asText()));
		for (JsonNode node : stepNodes) {
			String toolCode = require(node.path("toolCode").asText(null), "大模型返回了缺少工具编码的步骤");
			if (authorized.contains(toolCode)) continue;
			OperationAgentToolCatalog.ToolDefinition definition = tools.require(toolCode);
			Map<String, Object> config = objectMap(node.path("toolConfig"));
			if ("workflow.task.complete".equals(toolCode) && plannedTools.contains("workflow.instance.start")
					&& "${workflow.instance.start.taskId}".equals(config.get("taskId"))) {
				// 发起流程会产生待办，但不会向发起人返回可供代办的审核任务。
				omittedInvalidWorkflowApproval = true;
				continue;
			}
			normalizeExtendedPayload(toolCode, config, command.semesterCode());
			if ("form.definition.publish".equals(toolCode) && plannedTools.contains("form.definition.upsert")) {
				config.put("formId", "${form.definition.upsert.formId}");
			}
			if ("scheduling.review.submitted".equals(toolCode) && plannedTools.contains("scheduling.submit.best")) {
				config.put("candidateId", "${scheduling.submit.best.candidateId}");
			}
			if ("scheduling.submit.best".equals(toolCode) && plannedTools.contains("scheduling.generate.full")) {
				config.put("generatedCandidates", "${scheduling.generate.full.candidates}");
			}
			if ("scheduling.apply.approved".equals(toolCode) && plannedTools.contains("scheduling.review.submitted")) {
				config.put("candidateId", "${scheduling.review.submitted.candidateId}");
			}
			// 附件导入链必须沿用实际校验所得考生集合，不能扩大到同年级其他录取记录。
			if (Set.of("admission.class.assign.batch", "admission.review.batch", "admission.enroll.batch",
					"student.account.provision.batch")
					.contains(toolCode) && plannedTools.contains("admission.import.managed-file")) {
				config.put("candidateNos", "${admission.import.managed-file.candidateNos}");
				config.put("importRows", "${admission.import.managed-file.rows}");
				config.put("importWasDryRun", "${admission.import.managed-file.dryRun}");
			}
			if (Set.of("iam.authorization.assign", "iam.authorization.check").contains(toolCode)) {
				if (plannedTools.contains("iam.user.upsert")) {
					config.put("userCandidate", "${iam.user.upsert.username}");
					config.put("userCandidateOnly", "${iam.user.upsert.dryRun}");
				}
				if (plannedTools.contains("iam.role.upsert")) {
					config.put("roleCandidate", "${iam.role.upsert.roleCode}");
					config.put("roleCandidateOnly", "${iam.role.upsert.dryRun}");
				}
				if ("iam.authorization.check".equals(toolCode) && plannedTools.contains("iam.authorization.assign")) {
					config.put("proposedRoleCodes", "${iam.authorization.assign.roleCodes}");
				}
			}
			if (organizationScoped(toolCode) && command.organizationId() != null
					&& !command.organizationId().isBlank()) {
				config.put("organizationId", command.organizationId().trim());
			}
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
			attachments.stream().filter(value -> toolCode.equals(value.suggestedToolCode())).findFirst()
					.ifPresent(value -> config.put("managedFileId", value.managedFileId()));
			if ("iam.employee.import.managed-file".equals(toolCode) && config.containsKey("managedFileId")) {
				config.putIfAbsent("syncAccounts", true);
				config.putIfAbsent("disableInactiveAccounts", false);
			}
			if (Set.of("exam.sessions.create", "exam.invigilation.auto", "exam.conflicts.check",
					"exam.plan.publish").contains(toolCode)) {
				config.putIfAbsent("planId", "${exam.plan.upsert.planId}");
			}
			if (Set.of("grade.items.save", "grade.exam.sync", "grade.submit", "grade.review",
					"grade.publish").contains(toolCode)) {
				config.putIfAbsent("gradebookId", "${grade.gradebook.create.gradebookId}");
			}
			if ("grade.scheme.upsert".equals(toolCode) && plannedTools.contains("grade.gradebook.create")) {
				// 成绩册引用本次方案时，发布是建册的业务前置条件，仍随方案候选由本人确认。
				if (Boolean.FALSE.equals(config.get("publish")) || "false".equals(config.get("publish"))) {
					throw new IllegalArgumentException("草稿成绩方案不能用于创建成绩册，请先确认是否发布方案");
				}
				config.put("publish", true);
			}
			if ("grade.gradebook.create".equals(toolCode) && plannedTools.contains("grade.scheme.upsert")
					&& config.get("gradebook") instanceof Map<?, ?> gradebook) {
				Map<String, Object> payload = new LinkedHashMap<>();
				gradebook.forEach((key, value) -> payload.put(String.valueOf(key), value));
				payload.put("schemeId", "${grade.scheme.upsert.schemeId}");
				config.put("gradebook", payload);
			}
			applyExecutionMode(config, definition, candidateOnly);
			if (candidateOnly && FORMAL_ONLY_TOOLS.contains(toolCode)) {
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
		// 校验模型结构化声明的目标覆盖度，禁止通过关键词代替模型决定业务目标。
		for (JsonNode required : root.path("requiredTools")) {
			String code = required.asText();
			tools.require(code);
			// 正式审批和发布不属于候选执行范围，不能因此把可预演步骤判为遗漏。
			if (candidateOnly && FORMAL_ONLY_TOOLS.contains(code)) continue;
			if (omittedInvalidWorkflowApproval && "workflow.task.complete".equals(code)
					&& !authorized.contains(code)) continue;
			if (!authorized.contains(code)) {
				throw new IllegalStateException("模型规划遗漏已识别的业务目标：" + code + "，请重试");
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
		List<RequiredInput> requiredInputs = new ArrayList<>(requiredInputs(steps));
		for (ResolvedAttachment attachment : attachments) {
			if (attachment.recognized()) continue;
			requiredInputs.add(new RequiredInput("ATTACHMENT_CLASSIFICATION", "确认业务文件用途",
					"business.file.classify", attachment.managedFileId(), "文件用途",
					"无法识别“" + attachment.filename() + "”，请说明该文件要完成的业务或使用系统模板"));
		}
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
				readiness, matchedSkills(request, authorized), List.copyOf(requiredInputs),
				requiredInputs.isEmpty(), true, warnings);
	}

	private void normalizeExtendedPayload(String tool, Map<String, Object> config, String semesterCode) {
		String key = switch (tool) {
		case "exam.plan.upsert" -> "plan";
		case "grade.scheme.upsert" -> "scheme";
		case "grade.gradebook.create" -> "gradebook";
		case "grade.items.save" -> "itemsCommand";
		case "grade.change.request" -> "change";
		default -> null;
		};
		if (key == null) return;
		List<String> fields = switch (key) {
		case "plan" -> List.of("semesterCode", "planName", "examType", "startDate", "endDate",
				"baseInvigilators", "extraStaffThreshold", "allowOwnClassInvigilation", "ruleJson",
				"maxConsecutiveDuties", "campusTravelMinutes", "requireSubjectQualification");
		case "scheme" -> List.of("offeringId", "name", "totalScore", "passScore", "components", "rowVersion");
		case "gradebook" -> List.of("offeringId", "schemeId");
		case "change" -> List.of("courseGradeId", "afterScore", "reason");
		default -> List.of("items", "rowVersion");
		};
		Map<?, ?> source = config.get(key) instanceof Map<?, ?> nested ? nested : config;
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (String field : fields) {
			if (source.containsKey(field)) normalized.put(field, source.get(field));
		}
		if (!normalized.isEmpty()) {
			if ("plan".equals(key) && semesterCode != null) normalized.put("semesterCode", semesterCode);
			config.put(key, normalized);
		}
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
			List<ResolvedAttachment> attachments,
			Map<String, Object> readiness,
			boolean candidateOnly,
			String request,
			RuntimeException failure) {
		String goal = request.toLowerCase(java.util.Locale.ROOT);
		Set<String> selected = new LinkedHashSet<>();
		if (requestsTermCreation(request)) selected.add("academic.term.create");
		selected.addAll(requestedAcademicSetupTools(request));
		if (command.knowledgeBaseId() != null && !command.knowledgeBaseId().isBlank()) {
			selected.add("knowledge.policy.search");
		}
		attachments.stream().filter(ResolvedAttachment::recognized)
				.map(ResolvedAttachment::suggestedToolCode).forEach(selected::add);
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
		if (containsAny(goal, "考试收口", "期末考试检查")) selected.add("term.end.exam.check");
		if (containsAny(goal, "成绩发布", "成绩归档")) selected.add("term.end.grade.check");
		if (containsAny(goal, "归档", "学期交接")) selected.add("term.end.archive.check");
		if (containsAny(goal, "毕业")) selected.add("term.graduation.prepare");
		// “用户”会出现在多轮上下文前缀“用户补充”中，不能单独作为 IAM 意图。
		// 只有用户明确提到账号、角色或授权动作时才加载智能授权工具。
		if (containsAny(goal, "用户账号", "创建用户", "新增用户", "维护用户", "账号开通",
				"开通账号", "角色", "授权", "权限", "菜单权限", "数据范围")) {
			if (containsAny(goal, "创建用户", "新增用户", "维护用户", "开通账号")) selected.add("iam.user.upsert");
			if (containsAny(goal, "创建角色", "新增角色", "维护角色", "菜单权限", "业务授权", "数据范围")) {
				selected.add("iam.role.upsert");
			}
			selected.add("iam.authorization.check");
			if (containsAny(goal, "授予", "授权", "分配角色", "设置角色")) selected.add("iam.authorization.assign");
		}
		if (containsAny(goal, "动态表单", "流程表单", "发起流程", "办理流程", "审批流程")) {
			if (containsAny(goal, "创建", "设计", "维护")) selected.add("form.definition.upsert");
			if (containsAny(goal, "发布")) selected.add("form.definition.publish");
			if (containsAny(goal, "发起", "申请")) selected.add("workflow.instance.start");
			if (containsAny(goal, "填写", "保存表单", "节点表单")) selected.add("workflow.form.runtime.save");
			if (!selected.contains("workflow.instance.start") && containsAny(goal, "审批", "审核", "驳回"))
				selected.add("workflow.task.complete");
		}
		if (containsAny(goal, "考试计划", "考试安排", "考试场次", "考场", "考生", "监考")) {
			selected.addAll(List.of("exam.plan.upsert", "exam.sessions.create", "exam.rooms.create",
					"exam.candidates.assign", "exam.invigilation.auto", "exam.conflicts.check", "exam.plan.publish"));
		}
		if (containsAny(goal, "成绩录入", "成绩册", "成绩复核", "成绩审核", "成绩发布", "成绩更正")
				|| (goal.contains("成绩") && containsAny(goal, "录入", "复核", "审核", "发布", "更正"))) {
			if (containsAny(goal, "成绩方案", "完整成绩", "成绩录入")) {
				selected.add("grade.scheme.upsert");
				selected.add("grade.gradebook.create");
			}
			if (containsAny(goal, "成绩录入", "录入成绩")) selected.add("grade.items.save");
			if (containsAny(goal, "同步考试成绩")) selected.add("grade.exam.sync");
			if (containsAny(goal, "完整成绩", "提交成绩", "成绩复核", "成绩审核", "成绩发布")
					|| (goal.contains("成绩") && containsAny(goal, "提交", "复核", "审核", "发布"))) {
				selected.addAll(List.of("grade.submit", "grade.review", "grade.publish"));
			}
			if (containsAny(goal, "成绩更正", "更正成绩") || (goal.contains("成绩") && goal.contains("更正"))) {
				selected.addAll(List.of("grade.change.request", "grade.change.review"));
			}
		}
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
			if (selected.contains("admission.import.managed-file")
					&& Set.of("admission.class.assign.batch", "admission.review.batch", "admission.enroll.batch",
							"student.account.provision.batch").contains(toolCode)) {
				config.put("candidateNos", "${admission.import.managed-file.candidateNos}");
				config.put("importRows", "${admission.import.managed-file.rows}");
				config.put("importWasDryRun", "${admission.import.managed-file.dryRun}");
			}
			if ("academic.term.create".equals(toolCode)) {
				config.putAll(termCreationConfig(request, command.semesterCode()));
			}
			if (Set.of("academic.calendar.prepare", "academic.bell.configure").contains(toolCode)) {
				config.putAll(academicSetupConfig(
						toolCode, request, command.semesterCode(), command.organizationId(), candidateOnly));
			}
			if (organizationScoped(toolCode) && command.organizationId() != null
					&& !command.organizationId().isBlank()) {
				config.put("organizationId", command.organizationId().trim());
			}
			if (command.semesterCode() != null && !command.semesterCode().isBlank()) {
				config.put("semesterCode", command.semesterCode().trim());
			}
			if (command.admissionYear() != null && toolCode.startsWith("admission.")) {
				config.put("admissionYear", command.admissionYear());
			}
			attachments.stream().filter(value -> toolCode.equals(value.suggestedToolCode())).findFirst()
					.ifPresent(value -> config.put("managedFileId", value.managedFileId()));
			if ("iam.employee.import.managed-file".equals(toolCode)) config.put("syncAccounts", true);
			if (Set.of("exam.sessions.create", "exam.invigilation.auto", "exam.conflicts.check",
					"exam.plan.publish").contains(toolCode)) {
				config.put("planId", "${exam.plan.upsert.planId}");
			}
			if (Set.of("grade.items.save", "grade.exam.sync", "grade.submit", "grade.review",
					"grade.publish").contains(toolCode)) {
				config.put("gradebookId", "${grade.gradebook.create.gradebookId}");
			}
			applyExecutionMode(config, definition, candidateOnly);
			if (candidateOnly && FORMAL_ONLY_TOOLS.contains(toolCode)) {
				continue;
			}
			steps.add(new StepCommand("SAFE_STEP_" + order++, definition.name(),
					"平台根据用户目标和业务依赖选择该受控工具。", toolCode,
					definition.approvalRequired() && !candidateOnly,
					"业务工具返回可审计结果，异常时停在安全检查点。", config));
		}
		List<RequiredInput> requiredInputs = new ArrayList<>(requiredInputs(steps));
		for (ResolvedAttachment attachment : attachments) {
			if (!attachment.recognized()) requiredInputs.add(new RequiredInput(
					"ATTACHMENT_CLASSIFICATION", "确认业务文件用途", "business.file.classify",
					attachment.managedFileId(), "文件用途",
					"请说明“" + attachment.filename() + "”要完成的业务或使用系统模板"));
		}
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
				readiness, matchedSkills(request, new LinkedHashSet<>(authorized)),
				List.copyOf(requiredInputs), requiredInputs.isEmpty(), false, List.copyOf(warnings));
	}


	private List<OperationAgentSkillCatalog.SkillDefinition> matchedSkills(
			String request, Set<String> toolCodes) {
		return skills.match("", toolCodes);
	}

	private boolean containsAny(String source, String... values) {
		for (String value : values) if (source.contains(value)) return true;
		return false;
	}

	private boolean requestsTermCreation(String request) {
		return TERM_CREATION_PATTERN.matcher(request == null ? "" : request).find();
	}

	private List<String> requestedAcademicSetupTools(String request) {
		String source = request == null ? "" : request;
		List<String> result = new ArrayList<>();
		if (containsAny(source, "教学日历", "教学校历", "校历")) result.add("academic.calendar.prepare");
		if (containsAny(source, "校区作息", "作息方案", "上下课时间")) result.add("academic.bell.configure");
		return List.copyOf(result);
	}

	private Map<String, Object> academicSetupConfig(
			String toolCode, String request, String semesterCode, String organizationId, boolean candidateOnly) {
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("termId", "${academic.term.create.termId}");
		Map<String, Object> term = termCreationConfig(request, semesterCode);
		if ("academic.calendar.prepare".equals(toolCode)) {
			if (term.containsKey("startDate")) config.put("startDate", term.get("startDate"));
			if (term.containsKey("endDate")) config.put("endDate", term.get("endDate"));
		}
		if (organizationId != null && !organizationId.isBlank()) {
			config.put("organizationId", organizationId.trim());
		}
		if (candidateOnly) config.put("dryRun", true);
		return config;
	}

	/**
	 * 将学期创建语句转换成受控工具参数。只提取用户明确给出的值，
	 * 日期或周数缺失时由 requiredInputs 生成可理解的追问，绝不猜测正式数据。
	 */
	private Map<String, Object> termCreationConfig(String request, String semesterCode) {
		Map<String, Object> config = new LinkedHashMap<>();
		String localCode = localTermCode(semesterCode);
		if (localCode != null && localCode.matches("20\\d{2}-20\\d{2}-[12]")) {
			config.put("localTermCode", localCode);
			String academicYear = localCode.substring(0, 9);
			int termNo = Integer.parseInt(localCode.substring(localCode.length() - 1));
			config.put("academicYear", academicYear);
			config.put("termNo", termNo);
			config.put("termName", academicYear + "学年第" + (termNo == 1 ? "一" : "二") + "学期");
		}
		String source = request == null ? "" : request;
		Matcher compact = TERM_COMPACT_INPUT_PATTERN.matcher(source);
		Matcher range = TERM_DATE_RANGE_PATTERN.matcher(source);
		if (compact.find()) {
			config.put("startDate", normalizeDate(compact.group(1)));
			config.put("endDate", normalizeDate(compact.group(2)));
			config.put("weekCount", Integer.parseInt(compact.group(3)));
		} else if (range.find()) {
			config.put("startDate", normalizeDate(range.group(1)));
			config.put("endDate", normalizeDate(range.group(2)));
		} else {
			putDate(config, "startDate", TERM_START_DATE_PATTERN, source);
			putDate(config, "endDate", TERM_END_DATE_PATTERN, source);
		}
		Matcher weeks = TERM_WEEK_COUNT_PATTERN.matcher(source);
		if (weeks.find()) {
			String value = weeks.group(1) == null ? weeks.group(2) : weeks.group(1);
			config.put("weekCount", Integer.parseInt(value));
		} else if (config.containsKey("startDate") && config.containsKey("endDate")) {
			// 多轮对话中已经只剩教学周数待补充时，用户通常只会回答“20”。
			// 仅在开始、结束日期都已明确时才把末尾的裸数字解释为周数，避免误读年份或其他业务参数。
			Matcher bareWeek = TERM_BARE_WEEK_FOLLOW_UP_PATTERN.matcher(source);
			if (bareWeek.find()) config.put("weekCount", Integer.parseInt(bareWeek.group(1)));
		}
		if (containsAny(source, "设为当前学期", "设置为当前学期", "作为当前学期", "启用为当前学期")) {
			config.put("currentTerm", true);
		}
		return config;
	}

	private void putDate(Map<String, Object> config, String key, Pattern pattern, String source) {
		Matcher matcher = pattern.matcher(source);
		if (matcher.find()) config.put(key, normalizeDate(matcher.group(1)));
	}

	private String normalizeDate(String value) {
		String[] parts = value.replace('/', '-').replace('.', '-').replace("\u00a0", "").split("-");
		return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2])).toString();
	}

	private String localTermCode(String semesterCode) {
		String value = blank(semesterCode);
		if (value == null) return null;
		int separator = value.indexOf(':');
		return separator < 0 ? value : value.substring(separator + 1);
	}

	private boolean organizationScoped(String toolCode) {
		return !(toolCode.startsWith("workflow.") || toolCode.startsWith("form.")
				|| "iam.role.upsert".equals(toolCode));
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

	private void applyExecutionMode(Map<String, Object> config,
			OperationAgentToolCatalog.ToolDefinition definition, boolean candidateOnly) {
		if (!definition.writeTool()) return;
		// A candidate request can never write. In formal mode, omitted model flags
		// must not silently fall back to a business tool's preview default.
		if (candidateOnly) config.put("dryRun", true);
		else config.putIfAbsent("dryRun", false);
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
		String prompt = """
			解释用户对学校运营任务追加的条件，只输出JSON。
			支持字段 admissionYear(整数),semesterCode(字符串),batchLimit(1到500整数),
			blockWriteTools(布尔),llmRationale(简短说明)。未提到的字段不要输出，不得猜测。
			只读、禁止正式写入等语义应识别为blockWriteTools=true。
			""" + "\n用户条件：" + source;
		JsonNode parsed = parseObject(callModelAs(actor, () -> models.chat(null, prompt)));
		Map<String, Object> result = new LinkedHashMap<>();
		for (String key : List.of("admissionYear", "semesterCode", "batchLimit", "blockWriteTools", "llmRationale")) {
			if (parsed.hasNonNull(key)) result.put(key, objectMap(parsed).get(key));
		}
		if (parsed.has("batchLimit") && (!parsed.path("batchLimit").isIntegralNumber()
				|| parsed.path("batchLimit").asInt() < 1 || parsed.path("batchLimit").asInt() > 500)) {
			throw new IllegalArgumentException("模型返回的批量上限必须在1到500之间");
		}
		if (parsed.has("blockWriteTools") && !parsed.path("blockWriteTools").isBoolean()) {
			throw new IllegalArgumentException("模型返回的写入控制格式无效");
		}
		result.put("sourceText", source);
		result.put("modelGenerated", true);
		return result;
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

	private String toolPrompt(Set<String> allowed) {
		StringBuilder result = new StringBuilder();
		for (OperationAgentToolCatalog.ToolDefinition tool : tools.all()) {
			if (allowed != null && !allowed.contains(tool.code())) continue;
			result.append("- ").append(tool.code()).append(" | ").append(tool.name())
					.append(" | risk=").append(tool.riskLevel())
					.append(" | write=").append(tool.writeTool())
					.append(" | stage=").append(tool.businessStageOrder() == null ? "TERM_END" : tool.businessStageOrder())
					.append('/').append(tool.businessStageName() == null ? "期末收口" : tool.businessStageName())
					.append(" | required=").append(tool.inputRequirements().stream()
							.map(OperationAgentToolCatalog.InputRequirement::key).toList())
					.append('\n');
		}
		result.append("""
			IAM嵌套参数契约：
			iam.user.upsert.user: {username,password,displayName,email,phone,status,organizationId,accountType}；status是整数1启用/0禁用，不使用enabled。维护用户时省略未变更字段。角色授予必须使用iam.authorization.assign，禁止放入user.roleIds。
			iam.role.upsert.role: {roleCode,roleName,status,description,menuIds,permissionIds,workflowPermissionIds,dataScopes}；status为1/0。菜单和权限使用已核实的ID，未知时追问，禁止编造。
			iam.authorization.assign: {username,roleCodes:[角色编码],sourceKey}；用户/角色可由本计划前序工具创建。最后使用iam.authorization.check核验。
			动态表单参数契约：form.definition.upsert.form: {formKey,formName,version,description,fields:[{fieldKey,fieldLabel,fieldType,required,sortOrder}]}；普通文本用TEXT，required为布尔值。随后发布本次新建版本时，form.definition.publish.formId引用前序创建结果。
			对指定的已有排课候选办理提交、审核和应用时，使用用户提供的candidateId，不重新生成候选。正式发布仍须经过确认。
			""");
		return result.toString();
	}

	private String skillPrompt() {
		StringBuilder result = new StringBuilder();
		for (OperationAgentSkillCatalog.SkillDefinition skill : skills.all()) {
			result.append("- ").append(skill.code()).append(" | ").append(skill.name())
					.append(" | objective=").append(skill.objective())
					.append(" | tools=").append(skill.toolCodes()).append('\n');
		}
		return result.toString();
	}

	private List<ResolvedAttachment> resolveAttachments(DraftCommand command, Authentication authentication) {
		LinkedHashSet<String> ids = new LinkedHashSet<>();
		if (command.managedFileId() != null && !command.managedFileId().isBlank()) {
			ids.add(command.managedFileId().trim());
		}
		if (command.managedFileIds() != null) {
			command.managedFileIds().stream().filter(value -> value != null && !value.isBlank())
					.map(String::trim).forEach(ids::add);
		}
		List<ResolvedAttachment> result = new ArrayList<>();
		for (String id : ids) {
			OperationAgentManagedFileService.BusinessFileAnalysis analysis = managedFiles.analyze(id, authentication);
			result.add(new ResolvedAttachment(id, analysis.filename(), analysis.businessType(),
					analysis.businessTypeName(), analysis.suggestedToolCode(), analysis.dataRows(),
					analysis.recognized()));
		}
		return List.copyOf(result);
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

	/**
	 * Read timeouts only limit periods without network data. A provider can keep a
	 * chunked response open indefinitely by periodically sending bytes, so the
	 * interactive agent also enforces a wall-clock deadline and falls back to its
	 * deterministic Skill planner when that deadline is exceeded.
	 */
	private <T> T callModelAs(String actor, Supplier<T> call) {
		FutureTask<T> task = new FutureTask<>(() -> callAs(actor, call));
		Thread worker = Thread.ofVirtual().name("operation-agent-llm").start(task);
		try {
			return task.get(Math.max(1, interactiveModelTimeoutSeconds), TimeUnit.SECONDS);
		} catch (TimeoutException exception) {
			task.cancel(true);
			worker.interrupt();
			throw new IllegalStateException("大模型调用超过配置的等待期限", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("大模型规划被中断", exception);
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) throw runtimeException;
			throw new IllegalStateException("大模型规划失败", cause);
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
	private record ResolvedAttachment(
			String managedFileId,
			String filename,
			String businessType,
			String businessTypeName,
			String suggestedToolCode,
			int dataRows,
			boolean recognized) {}
	public record DraftCommand(
			String request,
			String knowledgeBaseId,
			String semesterCode,
			Integer admissionYear,
			String managedFileId,
			List<String> managedFileIds,
			Boolean candidateOnly,
			String organizationId) {
		public DraftCommand(String request, String knowledgeBaseId, String semesterCode,
				Integer admissionYear, String managedFileId, Boolean candidateOnly) {
			this(request, knowledgeBaseId, semesterCode, admissionYear, managedFileId, List.of(), candidateOnly, null);
		}
		public DraftCommand(String request, String knowledgeBaseId, String semesterCode,
				Integer admissionYear, String managedFileId, List<String> managedFileIds, Boolean candidateOnly) {
			this(request, knowledgeBaseId, semesterCode, admissionYear, managedFileId, managedFileIds, candidateOnly, null);
		}
	}
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
			List<OperationAgentSkillCatalog.SkillDefinition> matchedSkills,
			List<RequiredInput> requiredInputs,
			boolean readyToSave,
			boolean modelGenerated,
			List<String> warnings) {}
	public record StepDecision(String decision, String rationale, String nextAction) {}
}
