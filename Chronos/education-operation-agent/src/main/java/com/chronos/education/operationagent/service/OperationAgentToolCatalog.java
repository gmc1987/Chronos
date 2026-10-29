package com.chronos.education.operationagent.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OperationAgentToolCatalog {
	private static final Map<String, ToolDefinition> TOOLS = tools();

	private static Map<String, ToolDefinition> tools() {
		Map<String, ToolDefinition> values = new LinkedHashMap<>();
		add(values, "semester.context.check", "学期基础数据检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期编码"));
		add(values, "knowledge.policy.search", "校内政策知识检索", "LOW", false, false, 2, "自动重试", false);
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
				required("resources", "宿舍资源候选", "请提供楼栋、房间、楼层和床位数"));
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
		add(values, "term.graduation.prepare", "生成毕业学籍变更待办", "CRITICAL", true, true, 1, "幂等重试", false);
		add(values, "term.end.archive.check", "学期归档前置条件检查", "LOW", false, false, 2, "自动重试", false,
				required("semesterCode", "学期", "请指定需要检查的学期"));
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
		values.put(code, new ToolDefinition(code, name, riskLevel, approvalRequired, writeTool,
				maxAttempts, recoveryDescription, approvalActorExecutes,
				stage == null ? null : stage.order(), stage == null ? null : stage.code(), stage == null ? null : stage.name(),
				values.size() + 1, List.of(inputRequirements)));
	}

	private static InputRequirement required(String key, String label, String prompt) {
		return new InputRequirement(key, label, prompt);
	}

	private static StageDefinition stageFor(String toolCode) {
		if (Set.of("semester.context.check", "knowledge.policy.search", "teacher.onboarding.batch").contains(toolCode))
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
				.filter(requirement -> missing(values.get(requirement.key())))
				.toList();
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
