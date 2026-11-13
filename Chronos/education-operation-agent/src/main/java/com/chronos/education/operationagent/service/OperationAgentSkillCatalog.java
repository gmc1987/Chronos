package com.chronos.education.operationagent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Business skills describe an auditable workflow; tools remain the small,
 * permission-controlled operations used by that workflow. A skill never writes
 * data directly and can therefore be extended without giving the LLM a new
 * execution privilege.
 */
@Component
public class OperationAgentSkillCatalog {
	private static final Map<String, SkillDefinition> SKILLS = skills();

	private static Map<String, SkillDefinition> skills() {
		Map<String, SkillDefinition> values = new LinkedHashMap<>();
		add(values, "academic.term-setup", "学年学期与配套教学设置",
				"创建学年学期、准备教学日历及配置校区作息；支持多轮补充参数",
				Set.of(), List.of("academic.term.create", "academic.calendar.prepare", "academic.bell.configure"));
		add(values, "admission.onboarding", "新生入学办理",
				"接入录取名册、校验、分班、复查、建档、学号和学生账号",
				Set.of("新生", "录取", "入学", "分班", "学号", "学生账号"),
				List.of("admission.import.managed-file", "admission.data.check",
						"admission.class.assign.batch", "admission.review.batch",
						"admission.enroll.batch", "student.account.provision.batch",
						"account.binding.check"));
		add(values, "staff.onboarding", "教职工与任职初始化",
				"从文件导入教职工和任职，可同步普通员工账号和基础角色",
				Set.of("教职工", "员工", "工号", "名册", "普通账号", "任职"),
				List.of("iam.employee.import.managed-file", "account.binding.check"));
		add(values, "teacher.personnel.onboarding", "教师入职审批申请",
				"仅在明确要求办理教师入职时，生成指定人员的申请草稿，经发起人确认后提交独立审批",
				Set.of("教师入职", "教师建档", "开通教师权限"),
				List.of(OperationAgentTeacherOnboardingTool.CODE));
		add(values, "organization.maintenance", "组织岗位与负责人维护",
				"维护组织部门、负责人、岗位和职级基础数据",
				Set.of("组织", "部门", "负责人", "岗位", "职级"),
				List.of("iam.department.import.managed-file", "iam.position.import.managed-file",
						"iam.job-level.import.managed-file"));
		add(values, "semester.scheduling", "学期教学准备与走班排课",
				"核验教学任务，生成、审核、应用并发布课表",
				Set.of("教学任务", "开课", "走班", "排课", "课表"),
				List.of("teaching.assignment.generate", "teaching.offering.generate", "teaching.readiness.check", "scheduling.generate.full",
						"scheduling.submit.best", "scheduling.review.submitted",
						"scheduling.apply.approved", "scheduling.publish"));
		add(values, "admission.accommodation", "新生住宿资源与分配",
				"核验宿舍资源，生成住宿候选并在确认后批量分配",
				Set.of("住宿", "宿舍", "床位"),
				List.of("admission.accommodation.resource.check", "admission.accommodation.resource.maintain", "admission.accommodation.check",
						"admission.accommodation.plan", "admission.accommodation.assign.batch"));
		add(values, "term.closing", "学期末收口",
				"完成考试、成绩、学籍、毕业和归档准备度检查",
				Set.of("期末", "学期末", "收口", "毕业", "归档", "学期交接"),
				List.of("term.end.exam.check", "term.end.grade.check", "term.end.check",
						"term.graduation.prepare", "term.end.archive.check"));
		add(values, "identity.smart-authorization", "用户、角色与业务智能授权",
				"创建或维护用户与角色，配置菜单、原子权限、流程权限和数据范围，并按授权来源安全授予用户",
				Set.of("用户", "账号", "角色", "授权", "权限", "菜单", "数据范围", "业务权限"),
				List.of("iam.user.upsert", "iam.role.upsert", "iam.authorization.check",
						"iam.authorization.assign"));
		add(values, "workflow.dynamic-form-operation", "动态表单与流程完整办理",
				"维护并发布动态表单，填写主表单发起流程，保存节点表单并由任务办理人审批",
				Set.of("动态表单", "表单", "流程", "申请", "审批", "待办"),
				List.of("form.definition.upsert", "form.definition.publish", "workflow.instance.start",
						"workflow.form.runtime.save", "workflow.task.complete"));
		add(values, "exam.plan-lifecycle", "考试计划完整创建与发布",
				"创建考试计划、场次与考场，分配考生和监考，检查冲突并发布",
				Set.of("考试计划", "考试场次", "考场", "考生", "监考", "考试安排"),
				List.of("exam.plan.upsert", "exam.sessions.create", "exam.rooms.create",
						"exam.candidates.assign", "exam.invigilation.auto", "exam.conflicts.check",
						"exam.plan.publish"));
		add(values, "grade.full-lifecycle", "成绩录入、复核、发布与更正",
				"维护成绩方案和成绩册，录入或同步成绩，异人复核发布，并处理已发布成绩更正",
				Set.of("成绩录入", "成绩册", "成绩复核", "成绩审核", "成绩发布", "成绩更正"),
				List.of("grade.scheme.upsert", "grade.gradebook.create", "grade.items.save",
						"grade.exam.sync", "grade.submit", "grade.review.check", "grade.review", "grade.publish",
						"grade.change.request", "grade.change.review"));
		return Map.copyOf(values);
	}

	private static void add(Map<String, SkillDefinition> values, String code, String name,
			String objective, Set<String> triggers, List<String> toolCodes) {
		values.put(code, new SkillDefinition(code, name, objective, triggers, toolCodes));
	}

	public List<SkillDefinition> all() {
		return List.copyOf(SKILLS.values());
	}

	public List<SkillDefinition> match(String request) {
		String text = request == null ? "" : request.toLowerCase(java.util.Locale.ROOT);
		return SKILLS.values().stream()
				.filter(skill -> skill.triggers().stream().anyMatch(text::contains))
				.toList();
	}

	public List<SkillDefinition> match(String request, Set<String> toolCodes) {
		Set<String> codes = toolCodes == null ? Set.of() : toolCodes;
		String text = request == null ? "" : request.toLowerCase(java.util.Locale.ROOT);
		return SKILLS.values().stream()
				.filter(skill -> skill.triggers().stream().anyMatch(text::contains)
						|| skill.toolCodes().stream().anyMatch(codes::contains))
				.toList();
	}

	public record SkillDefinition(
			String code,
			String name,
			String objective,
			Set<String> triggers,
			List<String> toolCodes) {}
}
