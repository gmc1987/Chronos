package com.chronos.education.operationagent.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

import com.chronos.education.operationagent.model.OperationAgentCheckpointMode;

@Component
public class OperationAgentToolCatalog {
	private static final Map<String, ToolDefinition> TOOLS = tools();

	private static Map<String, ToolDefinition> tools() {
		Map<String, ToolDefinition> values = new LinkedHashMap<>();
		add(values, "academic.term.create", "创建学年学期", "HIGH", true, true, 1, "修正学期日期或编码后重试", false,
				required("organizationId", "目标机构", "请选择学期所属学校或校区"),
				required("localTermCode", "校内学期编码", "例如 2027-2028-1"),
				required("termName", "学期名称", "例如 2027—2028学年第一学期"),
				required("academicYear", "学年", "例如 2027-2028"),
				required("termNo", "学期序号", "第一学期填 1，第二学期填 2"),
				required("startDate", "开始日期", "请提供 YYYY-MM-DD 格式的学期开始日期"),
				required("endDate", "结束日期", "请提供 YYYY-MM-DD 格式的学期结束日期"),
				required("weekCount", "教学周数", "请提供教学周数，例如 20"));
		add(values, "academic.calendar.prepare", "生成学期教学日历", "HIGH", true, true, 1,
				"核验已公布的法定节假日数据后重试", false,
				required("termId", "目标学期", "请先创建或选择目标学期"));
		add(values, "academic.bell.configure", "配置校区作息方案", "HIGH", true, true, 1,
				"补充校区和节次时间后重试", false,
				required("termId", "目标学期", "请先创建或选择目标学期"),
				required("campusId", "适用校区", "请提供校区 ID、校区编码或准确名称"),
				required("scheduleName", "作息方案名称", "例如 2026 秋季标准作息"),
				required("periods", "节次时间", "请提供每一节的序号、名称、时段、开始时间和结束时间"));
		add(values, "semester.context.check", "学期基础数据检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期编码"));
		add(values, "knowledge.policy.search", "校内政策知识检索", "LOW", false, false, 2, "自动重试", false);
		add(values, "iam.department.import.managed-file", "从业务文件维护组织部门和负责人", "HIGH", true, true, 1, "修正文件后重新执行", false,
				required("managedFileId", "组织部门文件", "请上传组织部门 XLSX 文件"),
				required("organizationId", "目标机构", "请指定机构 ID、机构编码或准确名称"));
		add(values, "iam.position.import.managed-file", "从业务文件维护岗位", "HIGH", true, true, 1, "修正文件后重新执行", false,
				required("managedFileId", "岗位文件", "请上传岗位 XLSX 文件"),
				required("organizationId", "目标机构", "请指定机构 ID、机构编码或准确名称"));
		add(values, "iam.job-level.import.managed-file", "从业务文件维护职级", "HIGH", true, true, 1, "修正文件后重新执行", false,
				required("managedFileId", "职级文件", "请上传职级 XLSX 文件"),
				required("organizationId", "目标机构", "请指定机构 ID、机构编码或准确名称"));
		add(values, "iam.employee.import.managed-file", "从业务文件建立教职工、任职和账号", "CRITICAL", true, true, 1, "修正文件后重新执行", false,
				required("managedFileId", "教职工文件", "请上传教职工 XLSX 文件"),
				required("organizationId", "目标机构", "请指定机构 ID、机构编码或准确名称"));
		add(values, "teacher.onboarding.batch", "批量建立教师档案和账号", "CRITICAL", true, true, 1, "幂等重试", false);
		add(values, "admission.import.managed-file", "从受管文件导入新生名册", "HIGH", true, true, 1, "修正文件后重新执行", false,
				required("managedFileId", "新生名册", "请上传 XLSX 新生名册"));
		add(values, "admission.data.check", "新生录取数据检查", "LOW", false, false, 2, "自动重试", false);
		add(values, "admission.class.assign.batch", "按专业和班额批量拟分班", "HIGH", true, true, 1, "幂等重试", false);
		add(values, "admission.review.batch", "批量通过完整的新生资格复查", "HIGH", true, true, 1, "幂等重试", false);
		add(values, "admission.enroll.batch", "按拟分班批量报到建档并开通账号", "CRITICAL", true, true, 1, "幂等重试", false);
		add(values, "student.account.provision.batch", "批量补开学生账号", "HIGH", true, true, 1, "幂等重试", false);
		add(values, "account.binding.check", "学生与教师账号绑定完整性检查", "LOW", false, false, 2, "自动重试", false);
		add(values, "admission.accommodation.check", "新生住宿与床位容量检查", "LOW", false, false, 2, "维护宿舍床位后重试", false,
				required("semesterCode", "学期", "请指定住宿分配所属学期"),
				required("admissionYear", "入学年份", "请指定需要安排住宿的入学年份"));
		add(values, "admission.accommodation.resource.check", "宿舍楼房间床位数据核验", "LOW", false, false, 2, "修正宿舍资源后重试", false);
		add(values, "admission.accommodation.resource.maintain", "按候选维护宿舍资源并补齐床位", "HIGH", true, true, 1, "核查资源候选后重试", false,
				required("resources", "宿舍资源候选", "请提供楼栋、房间、楼层和床位数，性别规则使用 MIXED、MALE 或 FEMALE"));
		add(values, "admission.accommodation.plan", "生成新生住宿分配候选", "LOW", false, false, 2, "维护宿舍床位后重试", false,
				required("semesterCode", "学期", "请指定住宿分配所属学期"),
				required("admissionYear", "入学年份", "请指定需要安排住宿的入学年份"));
		add(values, "admission.accommodation.assign.batch", "确认并批量分配新生住宿", "HIGH", true, true, 1, "核查学生和床位冲突后重试", false,
				required("semesterCode", "学期", "请指定住宿分配所属学期"),
				required("assignments", "住宿分配候选", "请先确认学生与床位的分配候选"));
		add(values, "teaching.assignment.generate", "按确认映射生成教师分工", "HIGH", true, true, 1, "补充教师、学科和行政班映射后重试", false,
				required("semesterCode", "学期", "请指定教师分工所属学期"),
				required("assignments", "教师分工映射", "请提供教师工号、学科编码、行政班编码和周课时"));
		add(values, "teaching.offering.generate", "从教学任务生成开课与选课", "HIGH", true, true, 1, "幂等重试", false,
				required("semesterCode", "学期", "请指定开课所属学期"));
		add(values, "teaching.readiness.check", "教学任务与排课准备度检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期"));
		add(values, "scheduling.generate.full", "按全部规则生成排课候选", "MEDIUM", false, true, 1, "修正教学任务后重试", false,
				required("semesterCode", "学期", "请指定需要排课的学期"));
		add(values, "scheduling.submit.best", "提交最优排课候选审核", "HIGH", true, true, 1, "幂等重试", false,
				required("semesterCode", "学期", "请指定候选课表所属学期"));
		add(values, "scheduling.review.submitted", "独立审核排课候选", "HIGH", true, true, 1, "由审核人执行", true,
				required("semesterCode", "学期", "请指定待审核候选所属学期"));
		add(values, "scheduling.apply.approved", "应用已审核排课候选", "CRITICAL", true, true, 1, "核查冲突后重试", false,
				required("semesterCode", "学期", "请指定待应用候选所属学期"));
		add(values, "scheduling.publish", "发布正式课表版本", "CRITICAL", true, true, 1, "核查发布条件后重试", false,
				required("semesterCode", "学期", "请指定需要发布的学期"));
		add(values, "term.end.check", "期末成绩与学籍状态检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期"));
		add(values, "term.end.exam.check", "期末考试计划与监考收口检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期"));
		add(values, "term.end.grade.check", "期末成绩发布收口检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期"));
		add(values, "term.graduation.prepare", "生成毕业学籍变更待办", "CRITICAL", true, true, 1, "幂等重试", false,
				required("graduatingAdmissionYear", "毕业年级入学年份", "请明确要处理的毕业年级入学年份，例如2024"));
		add(values, "term.end.archive.check", "学期归档前置条件检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期"));
		add(values, "iam.user.upsert", "创建或维护平台用户", "CRITICAL", true, true, 1, "修正用户资料后重试", false,
				required("user", "用户资料", "请提供用户名、初始密码和账号资料"));
		add(values, "iam.role.upsert", "创建或维护角色及业务授权", "CRITICAL", true, true, 1, "修正角色授权范围后重试", false,
				required("role", "角色授权", "请提供角色、菜单、权限、流程权限和数据范围"));
		add(values, "iam.authorization.assign", "按智能授权来源授予用户角色", "CRITICAL", true, true, 1, "核对用户和角色后重试", false,
				required("username", "用户", "请指定需要授权的用户名"),
				required("roleCodes", "角色", "请指定需要授予的角色编码"));
		add(values, "iam.authorization.check", "核验用户有效角色与授权来源", "LOW", false, false, 2, "修正用户名后重试", false,
				required("username", "用户", "请指定需要核验的用户名"));
		add(values, "form.definition.upsert", "创建或维护动态表单及字段", "HIGH", true, true, 1, "修正表单字段后重试", false,
				required("form", "动态表单", "请提供表单编码、版本、名称和字段"));
		add(values, "form.definition.publish", "发布动态表单版本", "CRITICAL", true, true, 1, "完成表单校验后重试", false,
				required("formId", "表单", "请指定待发布的表单 ID"));
		add(values, "workflow.instance.start", "填写主表单并发起动态表单流程", "CRITICAL", true, true, 1, "补齐必填字段后重试", false,
				required("flowId", "流程", "请指定已发布流程定义 ID"),
				required("formData", "表单数据", "请提供流程主表单数据"));
		add(values, "workflow.form.runtime.save", "保存流程节点动态表单", "HIGH", true, true, 1, "按节点字段权限修正后重试", false,
				required("instanceId", "流程实例", "请指定流程实例 ID"),
				required("formId", "表单", "请指定当前节点表单 ID"),
				required("data", "表单数据", "请提供当前节点表单数据"));
		add(values, "workflow.task.complete", "审批或驳回动态表单流程任务", "CRITICAL", true, true, 1, "由当前任务办理人执行", true,
				required("taskId", "流程任务", "请指定当前待办任务 ID"));
		add(values, "exam.plan.upsert", "创建或维护考试计划", "HIGH", true, true, 1, "修正学期和考试日期后重试", false,
				required("plan", "考试计划", "请提供学期、名称、类型、日期和监考规则"));
		add(values, "exam.sessions.create", "批量创建考试场次并绑定开课", "HIGH", true, true, 1, "修正科目、时间和开课映射后重试", false,
				required("planId", "考试计划", "请指定考试计划 ID"),
				required("sessions", "考试场次", "请提供科目、日期、时间和开课映射"));
		add(values, "exam.rooms.create", "批量配置考试场次考场", "HIGH", true, true, 1, "修正场次和教室后重试", false,
				required("rooms", "考场配置", "请提供场次、教室和监考人数"));
		add(values, "exam.candidates.assign", "按学生或行政班分配考生座位", "HIGH", true, true, 1, "处理容量或学生冲突后重试", false,
				required("assignments", "考生分配", "请提供考场与学生或行政班映射"));
		add(values, "exam.invigilation.auto", "按规则自动分配监考", "HIGH", true, true, 1, "补充可用教师或资格后重试", false,
				required("planId", "考试计划", "请指定考试计划 ID"));
		add(values, "exam.conflicts.check", "检查考试计划资源与课程冲突", "LOW", false, false, 2, "处理冲突后重新检查", false,
				required("planId", "考试计划", "请指定考试计划 ID"));
		add(values, "exam.plan.publish", "发布完整考试计划", "CRITICAL", true, true, 1, "完成场次、考场、考生和监考后重试", false,
				required("planId", "考试计划", "请指定待发布考试计划 ID"));
		add(values, "grade.scheme.upsert", "创建或维护成绩评定方案", "HIGH", true, true, 1, "修正成绩构成和权重后重试", false,
				required("scheme", "成绩方案", "请提供开课、总分、及格线和成绩项目"));
		add(values, "grade.gradebook.create", "创建课程成绩册和学生快照", "HIGH", true, true, 1, "核对开课和成绩方案后重试", false,
				required("gradebook", "成绩册", "请提供开课 ID 和成绩方案 ID"));
		add(values, "grade.items.save", "批量录入或更新人工成绩", "CRITICAL", true, true, 1, "修正学生、成绩项目或分数后重试", false,
				required("gradebookId", "成绩册", "请指定成绩册 ID"),
				required("itemsCommand", "成绩明细", "请提供学生、成绩项目、分数和行版本"));
		add(values, "grade.exam.sync", "同步考试来源成绩", "HIGH", true, true, 1, "确认考试成绩和课程映射后重试", false,
				required("gradebookId", "成绩册", "请指定成绩册 ID"));
		add(values, "grade.submit", "提交成绩册复核", "CRITICAL", true, true, 1, "补齐全部成绩后重试", false,
				required("gradebookId", "成绩册", "请指定待提交成绩册 ID"));
		add(values, "grade.review.check", "只读检查当前账号是否可复核成绩册", "LOW", false, false, 1, "核对办理人", false,
				required("gradebookId", "成绩册", "请指定待复核成绩册 ID"),
				required("taskId", "审核任务", "请指定成绩复核任务 ID"));
		add(values, "grade.review", "独立复核成绩册", "CRITICAL", true, true, 1, "由非任课教师审核", true,
				required("gradebookId", "成绩册", "请指定待复核成绩册 ID"),
				required("taskId", "审核任务", "请指定成绩复核任务 ID"));
		add(values, "grade.publish", "发布审核通过的成绩", "CRITICAL", true, true, 1, "完成独立复核后重试", false,
				required("gradebookId", "成绩册", "请指定待发布成绩册 ID"));
		add(values, "grade.change.request", "发起已发布成绩更正", "CRITICAL", true, true, 1, "修正成绩和更正原因后重试", false,
				required("gradebookId", "成绩册", "请指定已发布成绩册 ID"),
				required("change", "成绩更正", "请提供原成绩 ID、更正后分数和原因"));
		add(values, "grade.change.review", "独立复核成绩更正", "CRITICAL", true, true, 1, "由成绩更正审核人执行", true,
				required("requestId", "成绩更正单", "请指定成绩更正单 ID"),
				required("instanceId", "流程实例", "请指定成绩更正流程实例 ID"),
				required("decision", "审核决定", "请提供任务 ID、审核结论和意见"));
		return Map.copyOf(values);
	}

	private static void add(
			Map<String, ToolDefinition> values,
			String code,
			String name,
			String riskLevel,
			boolean approvalRequired,
			boolean writeTool,
			int maxAttempts,
			String recoveryDescription,
			boolean approvalActorExecutes,
			InputRequirement... inputRequirements) {
		StageDefinition stage = stageFor(code);
		OperationAgentCheckpointMode checkpointMode = checkpointModeFor(code, approvalRequired);
		values.put(code, new ToolDefinition(code, name, riskLevel, approvalRequired, checkpointMode, writeTool,
				maxAttempts, recoveryDescription, approvalActorExecutes,
				stage == null ? null : stage.order(), stage == null ? null : stage.code(), stage == null ? null : stage.name(),
				values.size() + 1, List.of(inputRequirements)));
	}

	/**
	 * 只有业务本身要求独立复核，或权限变更属于受保护操作时，才进入异人审核。
	 * 其他正式写入默认由发起人核对候选结果后确认。流程任务由流程引擎自身
	 * 校验办理人，不能再套一层通用智能体审核。
	 */
	private static OperationAgentCheckpointMode checkpointModeFor(String code, boolean approvalRequired) {
		if (!approvalRequired) return OperationAgentCheckpointMode.NONE;
		if ("workflow.task.complete".equals(code)) {
			return OperationAgentCheckpointMode.BUSINESS_WORKFLOW;
		}
		if (Set.of(
				"scheduling.review.submitted",
				"grade.review",
				"grade.change.review",
				"admission.review.batch",
				"iam.role.upsert",
				"iam.authorization.assign").contains(code)) {
			return OperationAgentCheckpointMode.DUAL_CONTROL;
		}
		return OperationAgentCheckpointMode.INITIATOR_CONFIRM;
	}

	private static InputRequirement required(String key, String label, String prompt) {
		return new InputRequirement(key, label, prompt);
	}

	private static StageDefinition stageFor(String toolCode) {
		if (Set.of("academic.term.create", "academic.calendar.prepare", "academic.bell.configure",
				"semester.context.check", "knowledge.policy.search",
				"iam.department.import.managed-file", "iam.position.import.managed-file",
				"iam.job-level.import.managed-file", "iam.employee.import.managed-file",
				"teacher.onboarding.batch").contains(toolCode))
			return new StageDefinition(1, "FOUNDATION", "基础数据准备");
		if (Set.of("admission.import.managed-file", "admission.data.check").contains(toolCode))
			return new StageDefinition(2, "ADMISSION_IMPORT", "新生数据接入");
		if ("admission.class.assign.batch".equals(toolCode))
			return new StageDefinition(3, "CLASS_ASSIGNMENT", "行政班分班");
		if (Set.of("admission.review.batch", "admission.enroll.batch", "student.account.provision.batch", "account.binding.check", "admission.accommodation.check", "admission.accommodation.plan", "admission.accommodation.assign.batch", "admission.accommodation.resource.check", "admission.accommodation.resource.maintain").contains(toolCode))
			return new StageDefinition(4, "ENROLLMENT_ACCOUNT", "报到、学籍、学号及账号");
		if ("teaching.assignment.generate".equals(toolCode))
			return new StageDefinition(5, "TEACHING_ASSIGNMENT", "教学任务与教师安排");
		if (Set.of("teaching.offering.generate", "teaching.readiness.check").contains(toolCode))
			return new StageDefinition(6, "TEACHING_CLASS", "教学班与选课关系");
		if (toolCode.startsWith("scheduling."))
			return new StageDefinition(7, "SCHEDULING", "自动排课、审核与发布");
		if (Set.of("term.end.check", "term.end.exam.check", "term.end.grade.check").contains(toolCode))
			return new StageDefinition(8, "TERM_END_CLOSE", "期末考试、成绩与学籍收口");
		if ("term.graduation.prepare".equals(toolCode))
			return new StageDefinition(9, "GRADUATION_STATUS", "毕业学籍变更");
		if ("term.end.archive.check".equals(toolCode))
			return new StageDefinition(10, "ARCHIVE_READINESS", "归档与下学期交接");
		if (toolCode.startsWith("iam.")) return new StageDefinition(1, "IAM_AUTHORIZATION", "用户、角色与业务授权");
		if (toolCode.startsWith("form.") || toolCode.startsWith("workflow."))
			return new StageDefinition(2, "DYNAMIC_FORM", "动态表单与流程办理");
		if (toolCode.startsWith("exam.")) return new StageDefinition(8, "EXAM_PLAN", "考试计划创建与发布");
		if (toolCode.startsWith("grade.")) return new StageDefinition(9, "GRADE_OPERATION", "成绩录入、复核与更正");
		return null;
	}

	public List<ToolDefinition> all() {
		return TOOLS.values().stream().sorted(Comparator.comparingInt(ToolDefinition::executionOrder)).toList();
	}

	/**
	 * 返回平台确定的工具执行顺序。模型输出、人工保存校验和执行界面都应以工具目录为准，
	 * 避免各处维护不同的阶段顺序。
	 */
	public int executionOrder(String code) {
		return require(code).executionOrder();
	}

	public ToolDefinition require(String code) {
		ToolDefinition value = TOOLS.get(code);
		if (value == null) throw new IllegalArgumentException("未授权或未注册的业务工具：" + code);
		return value;
	}

	public void validateAuthorized(Set<String> authorized, String code) {
		require(code);
		if (!authorized.contains(code)) throw new IllegalArgumentException("执行计划未授权工具：" + code);
	}

	public List<InputRequirement> missingInputs(String code, Map<String, Object> config) {
		Map<String, Object> values = config == null ? Map.of() : config;
		return require(code).inputRequirements().stream()
				.filter(requirement -> missing(values.get(requirement.key()))
						|| incompleteStructuredInput(code, requirement.key(), values.get(requirement.key())))
				.toList();
	}

	private boolean incompleteStructuredInput(String code, String key, Object value) {
		return switch (code + ":" + key) {
		case "grade.scheme.upsert:scheme" -> lacksFields(value, "offeringId", "name", "totalScore",
				"passScore", "components");
		case "grade.gradebook.create:gradebook" -> lacksFields(value, "offeringId", "schemeId");
		case "grade.items.save:itemsCommand" -> lacksFields(value, "items")
				|| ((Map<?, ?>) value).get("items") instanceof List<?> items
				&& items.stream().anyMatch(item -> lacksFields(item, "studentId", "componentId"));
		case "exam.plan.upsert:plan" -> lacksFields(value, "semesterCode", "planName", "examType",
				"startDate", "endDate");
		case "exam.rooms.create:rooms" -> !(value instanceof List<?> rooms)
				|| rooms.stream().anyMatch(room -> lacksFields(room, "sessionId", "classroomId"));
		case "admission.accommodation.resource.maintain:resources" -> !(value instanceof List<?> resources)
				|| resources.stream().anyMatch(room -> lacksFields(room, "buildingCode", "buildingName",
						"roomNo", "floorNo", "bedCount") || invalidDormGenderPolicy((Map<?, ?>) room));
		default -> false;
		};
	}

	private boolean invalidDormGenderPolicy(Map<?, ?> room) {
		Object policy = room.get("genderPolicy");
		return !missing(policy) && !Set.of("MIXED", "MALE", "FEMALE").contains(policy);
	}

	private boolean lacksFields(Object value, String... fields) {
		if (!(value instanceof Map<?, ?> map)) return true;
		for (String field : fields) {
			if (missing(map.get(field))) return true;
		}
		return false;
	}

	private boolean missing(Object value) {
		if (value == null) return true;
		if (value instanceof String text) return text.isBlank();
		if (value instanceof java.util.Collection<?> collection) return collection.isEmpty();
		return false;
	}

	public record ToolDefinition(
			String code,
			String name,
			String riskLevel,
			boolean approvalRequired,
			OperationAgentCheckpointMode checkpointMode,
			boolean writeTool,
			int maxAttempts,
			String recoveryDescription,
			boolean approvalActorExecutes,
			Integer businessStageOrder,
			String businessStageCode,
			String businessStageName,
			int executionOrder,
			List<InputRequirement> inputRequirements) {}
	public record InputRequirement(String key, String label, String prompt) {}
	private record StageDefinition(int order, String code, String name) {}
}
