package com.chronos.education.operationagent.service;

import static com.chronos.education.grade.dto.GradeDtos.*;
import static com.chronos.education.grade.dto.GradeProductionDtos.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.grade.service.GradeProductionService;
import com.chronos.education.scheduling.model.ExamCommands;
import com.chronos.education.scheduling.service.ExamCenterService;
import com.chronos.form.FormService;
import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.dto.RoleDTO;
import com.chronos.model.form.FormDefinition;
import com.chronos.model.form.FormField;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;
import com.chronos.service.impl.AdminUserServiceImpl;
import com.chronos.service.impl.RoleServiceImpl;
import com.chronos.service.impl.UserRoleAssignmentService;
import com.chronos.workflow.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.security.DirectoryDataScopeGuard;

/**
 * Production business tools that were added after the original semester
 * preparation tool set. Every formal write delegates to the owning domain
 * service; dry-run calls only validate and return reviewable candidate data.
 */
@Service
public class OperationAgentExtendedBusinessTools {
	private static final Set<String> SUPPORTED = Set.of(
			"iam.user.upsert", "iam.role.upsert", "iam.authorization.check", "iam.authorization.assign",
			"form.definition.upsert", "form.definition.publish", "workflow.instance.start",
			"workflow.form.runtime.save", "workflow.task.complete",
			"grade.scheme.upsert", "grade.gradebook.create", "grade.items.save", "grade.exam.sync",
			"grade.submit", "grade.review.check", "grade.review", "grade.publish", "grade.change.request", "grade.change.review",
			"exam.plan.upsert", "exam.sessions.create", "exam.rooms.create", "exam.candidates.assign",
			"exam.invigilation.auto", "exam.conflicts.check", "exam.plan.publish");

	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final AdminUserServiceImpl userService;
	private final RoleServiceImpl roleService;
	private final UserRoleAssignmentService roleAssignments;
	private final IFormDefinitionRepository formDefinitions;
	private final FormService forms;
	private final WorkflowService workflows;
	private final GradeCenterService grades;
	private final GradeProductionService gradeProduction;
	private final ExamCenterService exams;
	private final ObjectMapper mapper;
	private final DirectoryDataScopeGuard dataScopes;

	public OperationAgentExtendedBusinessTools(
			IAdminUserRepository users,
			IRoleRepository roles,
			AdminUserServiceImpl userService,
			RoleServiceImpl roleService,
			UserRoleAssignmentService roleAssignments,
			IFormDefinitionRepository formDefinitions,
			FormService forms,
			WorkflowService workflows,
			GradeCenterService grades,
			GradeProductionService gradeProduction,
			ExamCenterService exams,
			ObjectMapper mapper,
			DirectoryDataScopeGuard dataScopes) {
		this.users = users;
		this.roles = roles;
		this.userService = userService;
		this.roleService = roleService;
		this.roleAssignments = roleAssignments;
		this.formDefinitions = formDefinitions;
		this.forms = forms;
		this.workflows = workflows;
		this.grades = grades;
		this.gradeProduction = gradeProduction;
		this.exams = exams;
		this.mapper = mapper;
		this.dataScopes = dataScopes;
	}

	public boolean supports(String toolCode) {
		return SUPPORTED.contains(toolCode);
	}

	@Transactional
	public Map<String, Object> execute(String toolCode, Map<String, Object> config, String actor) {
		boolean dryRun = bool(config.get("dryRun"), true);
		return switch (toolCode) {
		case "iam.user.upsert" -> upsertUser(config, actor, dryRun);
		case "iam.role.upsert" -> upsertRole(config, dryRun);
		case "iam.authorization.check" -> authorizationCheck(config, actor);
		case "iam.authorization.assign" -> assignAuthorization(config, actor, dryRun);
		case "form.definition.upsert" -> upsertForm(config, dryRun);
		case "form.definition.publish" -> publishForm(config, dryRun);
		case "workflow.instance.start" -> startWorkflow(config, actor, dryRun);
		case "workflow.form.runtime.save" -> saveRuntimeForm(config, actor, dryRun);
		case "workflow.task.complete" -> completeWorkflowTask(config, actor, dryRun);
		case "grade.scheme.upsert" -> upsertGradeScheme(config, actor, dryRun);
		case "grade.gradebook.create" -> createGradebook(config, actor, dryRun);
		case "grade.items.save" -> saveGradeItems(config, actor, dryRun);
		case "grade.exam.sync" -> syncExamGrades(config, actor, dryRun);
		case "grade.submit" -> submitGrades(config, actor, dryRun);
		case "grade.review.check" -> checkGradeReview(config, actor);
		case "grade.review" -> reviewGrades(config, actor, dryRun);
		case "grade.publish" -> publishGrades(config, actor, dryRun);
		case "grade.change.request" -> requestGradeChange(config, actor, dryRun);
		case "grade.change.review" -> reviewGradeChange(config, actor, dryRun);
		case "exam.plan.upsert" -> upsertExamPlan(config, dryRun);
		case "exam.sessions.create" -> createExamSessions(config, dryRun);
		case "exam.rooms.create" -> createExamRooms(config, dryRun);
		case "exam.candidates.assign" -> assignExamCandidates(config, dryRun);
		case "exam.invigilation.auto" -> autoAssignInvigilation(config, dryRun);
		case "exam.conflicts.check" -> examConflicts(config, dryRun);
		case "exam.plan.publish" -> publishExamPlan(config, actor, dryRun);
		default -> throw new IllegalArgumentException("未注册的扩展业务工具：" + toolCode);
		};
	}

	private Map<String, Object> upsertUser(Map<String, Object> config, String actor, boolean dryRun) {
		Map<String, Object> spec = statusSpec(payload(config, "user"));
		if (spec.get("roleIds") instanceof java.util.Collection<?> roleIds && !roleIds.isEmpty()) {
			throw new IllegalArgumentException("用户资料工具不能分配角色，请使用智能授权工具");
		}
		// 资料维护不能顺带清空人工角色来源；授权只经过独立的受保护工具。
		spec.remove("roleIds");
		AdminUserDTO command = convert(spec, AdminUserDTO.class);
		if (blank(command.getUsername())) throw new IllegalArgumentException("用户名不能为空");
		String organizationId = required(config.get("organizationId"), "目标机构");
		dataScopes.requireOrganization(authentication(actor), organizationId);
		if (blank(command.getOrganizationId())) command.setOrganizationId(organizationId);
		if (!organizationId.equals(command.getOrganizationId()))
			throw new IllegalArgumentException("用户所属机构与智能体任务目标机构不一致");
		AdminUser existing = users.findByUsername(command.getUsername().trim());
		if (existing != null && !organizationId.equals(existing.getOrganizationId()))
			throw new IllegalArgumentException("不能跨机构修改用户账号");
		if (existing != null) command.setId(existing.getId());
		if (dryRun) return evidence("用户候选已通过格式校验", "username", command.getUsername(),
				"operation", existing == null ? "CREATE" : "UPDATE", "passwordSupplied", !blank(command.getPassword()), "dryRun", true);
		if (existing == null) userService.save(command); else userService.update(command);
		AdminUser saved = users.findByUsername(command.getUsername().trim());
		roleAssignments.ensureBaseAccountRole(saved);
		return evidence("用户已保存", "userId", saved.getId(), "username", saved.getUsername(),
				"operation", existing == null ? "CREATED" : "UPDATED", "dryRun", false);
	}

	private Map<String, Object> upsertRole(Map<String, Object> config, boolean dryRun) {
		RoleDTO command = convert(statusSpec(payload(config, "role")), RoleDTO.class);
		if (blank(command.getRoleCode()) || blank(command.getRoleName())) {
			throw new IllegalArgumentException("角色编码和名称不能为空");
		}
		Role existing = roles.findByRoleCode(command.getRoleCode().trim());
		if (existing != null) command.setId(existing.getId());
		if (dryRun) return evidence("角色及业务授权候选已生成", "roleCode", command.getRoleCode(),
				"operation", existing == null ? "CREATE" : "UPDATE",
				"menuCount", size(command.getMenuIds()), "permissionCount", size(command.getPermissionIds()),
				"workflowPermissionCount", size(command.getWorkflowPermissionIds()),
				"dataScopeCount", size(command.getDataScopes()), "dryRun", true);
		if (existing == null) roleService.save(command); else roleService.update(command);
		Role saved = roles.findByRoleCode(command.getRoleCode().trim());
		return evidence("角色及业务授权已保存", "roleId", saved.getId(), "roleCode", saved.getRoleCode(),
				"operation", existing == null ? "CREATED" : "UPDATED", "dryRun", false);
	}

	private Map<String, Object> authorizationCheck(Map<String, Object> config, String actor) {
		String username = required(config.get("username"), "用户名");
		if (users.findByUsername(username) == null && pendingUser(config, username)) {
			dataScopes.requireOrganization(authentication(actor), required(config.get("organizationId"), "目标机构"));
			return evidence("用户和角色尚处于候选，正式创建及授权后将重新核验有效权限", "username", username,
					"dryRun", true, "effective", false, "proposedRoleCodes", strings(config.get("proposedRoleCodes")));
		}
		AdminUser user = requireUser(config);
		requireTargetUserOrganization(config, actor, user);
		List<Map<String, Object>> effective = user.getRoles() == null ? List.of() : user.getRoles().stream()
				.map(role -> evidence(null, "roleId", role.getId(), "roleCode", role.getRoleCode(),
						"roleName", role.getRoleName())).toList();
		return evidence("用户有效授权已核验", "userId", user.getId(), "username", user.getUsername(),
				"roles", effective, "manualRoleIds", roleAssignments.manualRoleIds(user.getId()));
	}

	private Map<String, Object> assignAuthorization(Map<String, Object> config, String actor, boolean dryRun) {
		if (dryRun) {
			String username = required(config.get("username"), "用户名");
			AdminUser existing = users.findByUsername(username);
			if (existing == null && !pendingUser(config, username)) {
				throw new IllegalArgumentException("用户不存在，且本计划没有该用户的前置候选：" + username);
			}
			if (existing != null) requireTargetUserOrganization(config, actor, existing);
			else dataScopes.requireOrganization(authentication(actor), required(config.get("organizationId"), "目标机构"));
			List<String> roleCodes = strings(config.get("roleCodes"));
			if (roleCodes.isEmpty()) throw new IllegalArgumentException("请提供需要授予的角色编码");
			for (String roleCode : roleCodes) {
				if (roles.findByRoleCode(roleCode) == null && !(Boolean.TRUE.equals(config.get("roleCandidateOnly"))
						&& roleCode.equals(text(config.get("roleCandidate"))))) {
					throw new IllegalArgumentException("角色不存在，且本计划没有该角色的前置候选：" + roleCode);
				}
			}
			return evidence("用户角色授权候选已生成，尚未生效", "username", username, "roleCodes", roleCodes,
					"sourceKey", text(config.getOrDefault("sourceKey", "OPERATION_AGENT")), "dryRun", true,
					"userPendingCreation", existing == null, "effective", false);
		}
		AdminUser user = requireUser(config);
		requireTargetUserOrganization(config, actor, user);
		List<String> roleCodes = strings(config.get("roleCodes"));
		if (roleCodes.isEmpty()) throw new IllegalArgumentException("请提供需要授予的角色编码");
		List<Role> requested = roleCodes.stream().map(code -> {
			Role role = roles.findByRoleCode(code);
			if (role == null) throw new IllegalArgumentException("角色不存在：" + code);
			return role;
		}).toList();
		String sourceKey = text(config.getOrDefault("sourceKey", "OPERATION_AGENT"));
		if (dryRun) return evidence("用户角色授权候选已生成", "username", user.getUsername(),
				"roleCodes", roleCodes, "sourceKey", sourceKey);
		roleAssignments.replaceAgentRoles(user, requested.stream().map(Role::getId).toList(), sourceKey);
		return evidence("用户角色授权已生效", "username", user.getUsername(), "roleCodes", roleCodes,
				"sourceType", UserRoleAssignmentService.AGENT_GRANT, "sourceKey", sourceKey);
	}

	private boolean pendingUser(Map<String, Object> config, String username) {
		return Boolean.TRUE.equals(config.get("userCandidateOnly"))
				&& username.equals(text(config.get("userCandidate")));
	}

	/** 兼容模型结构化启停字段；冲突不静默覆盖，未知字段仍由DTO校验拒绝。 */
	private Map<String, Object> statusSpec(Map<String, Object> source) {
		Map<String, Object> spec = new LinkedHashMap<>(source);
		if (spec.containsKey("enabled")) {
			Object enabled = spec.remove("enabled");
			if (!(enabled instanceof Boolean value)) throw new IllegalArgumentException("enabled必须是布尔值");
			int status = value ? 1 : 0;
			if (spec.containsKey("status") && !String.valueOf(status).equals(String.valueOf(spec.get("status")))) {
				throw new IllegalArgumentException("enabled与status启停设置冲突");
			}
			spec.put("status", status);
		}
		return spec;
	}

	private Map<String, Object> upsertForm(Map<String, Object> config, boolean dryRun) {
		Map<String, Object> spec = new LinkedHashMap<>(payload(config, "form"));
		formAlias(spec, "formKey", "code");
		formAlias(spec, "formName", "name");
		String formKey = required(spec.get("formKey"), "表单编码");
		String formName = required(spec.get("formName"), "表单名称");
		String version = text(spec.getOrDefault("version", "v1"));
		FormDefinition existing = formDefinitions.findByFormKeyOrderByCreateTimeDesc(formKey).stream()
				.filter(value -> version.equals(value.getVersion())).findFirst().orElse(null);
		List<Map<String, Object>> fieldSpecs = maps(spec.get("fields")).stream().map(source -> {
			Map<String, Object> field = new LinkedHashMap<>(source);
			formAlias(field, "fieldKey", "code");
			formAlias(field, "fieldLabel", "name");
			formAlias(field, "fieldType", "type");
			return field;
		}).toList();
		if (fieldSpecs.isEmpty()) throw new IllegalArgumentException("动态表单至少需要一个字段");
		validateFieldSpecs(fieldSpecs);
		if (dryRun) return evidence("动态表单候选已生成", "formKey", formKey, "formName", formName, "version", version,
				"formId", "candidate:form:" + formKey + ":" + version, "dryRun", true,
				"operation", existing == null ? "CREATE" : "UPDATE", "fields", fieldSpecs);
		FormDefinition definition = existing == null ? new FormDefinition() : existing;
		definition.setFormKey(formKey);
		definition.setFormName(formName);
		definition.setVersion(version);
		definition.setDescription(text(spec.get("description")));
		definition = existing == null ? forms.save(definition) : forms.update(definition);
		Map<String, FormField> current = forms.fields(definition.getId()).stream()
				.collect(java.util.stream.Collectors.toMap(FormField::getFieldKey, value -> value));
		Set<String> retained = new LinkedHashSet<>();
		for (Map<String, Object> fieldSpec : fieldSpecs) {
			String key = required(fieldSpec.get("fieldKey"), "字段编码");
			retained.add(key);
			FormField field = current.getOrDefault(key, new FormField());
			field.setFormId(definition.getId());
			field.setFieldKey(key);
			field.setFieldLabel(required(fieldSpec.get("fieldLabel"), "字段名称"));
			field.setFieldType(text(fieldSpec.getOrDefault("fieldType", "TEXT")).toUpperCase());
			field.setSortOrder(number(fieldSpec.get("sortOrder"), retained.size()));
			field.setRequired(bool(fieldSpec.get("required"), false));
			field.setOptionsJson(json(fieldSpec.get("options"), fieldSpec.get("optionsJson")));
			if (field.getId() == null) forms.saveField(field); else forms.updateField(field);
		}
		if (bool(spec.get("replaceFields"), true)) {
			current.values().stream().filter(value -> !retained.contains(value.getFieldKey()))
					.forEach(value -> forms.deleteField(value.getId()));
		}
		return evidence("动态表单已保存", "formId", definition.getId(), "formKey", formKey,
				"version", version, "fieldCount", retained.size());
	}

	private void formAlias(Map<String, Object> spec, String canonical, String alias) {
		if (spec.containsKey(alias)) spec.putIfAbsent(canonical, spec.get(alias));
	}

	private Map<String, Object> publishForm(Map<String, Object> config, boolean dryRun) {
		String formId = required(config.get("formId"), "表单 ID");
		if (formId.startsWith("candidate:form:")) {
			if (!dryRun) throw new IllegalArgumentException("表单尚未正式保存，不能发布");
			return evidence("表单发布候选已生成；正式保存后会重新核验，当前未发布",
					"formId", formId, "dryRun", true, "status", "PENDING_FORM_CREATION");
		}
		FormDefinition definition = forms.definition(formId);
		if (dryRun) return evidence("动态表单发布候选已核验", "formId", formId,
				"formKey", definition.getFormKey(), "status", definition.getStatus());
		FormDefinition published = forms.publish(formId);
		return evidence("动态表单已发布", "formId", published.getId(), "status", published.getStatus());
	}

	private Map<String, Object> startWorkflow(Map<String, Object> config, String actor, boolean dryRun) {
		String flowId = required(config.get("flowId"), "流程定义 ID");
		Map<String, Object> data = map(config.get("formData"));
		Map<String, Object> startForm = workflows.startForm(flowId, actor);
		if (dryRun) return evidence("流程发起候选已生成", "flowId", flowId,
				"startForm", startForm, "formData", data);
		var instance = workflows.start(flowId, text(config.get("businessKey")), "{}", data, actor);
		return evidence("流程已发起", "instanceId", instance.getId(), "businessKey", instance.getBusinessKey(),
				"status", instance.getStatus());
	}

	private Map<String, Object> saveRuntimeForm(Map<String, Object> config, String actor, boolean dryRun) {
		String instanceId = required(config.get("instanceId"), "流程实例 ID");
		String formId = required(config.get("formId"), "表单 ID");
		Map<String, Object> data = map(config.get("data"));
		Map<String, Object> runtime = workflows.runtimeForms(instanceId, actor);
		if (dryRun) return evidence("运行时表单候选已核验", "instanceId", instanceId,
				"formId", formId, "runtime", runtime, "data", data);
		var saved = workflows.saveRuntimeForm(instanceId, formId, data, bool(config.get("draft"), false), actor);
		return evidence("运行时表单已保存", "instanceId", instanceId, "formId", formId,
				"formInstanceId", saved.getId());
	}

	private Map<String, Object> completeWorkflowTask(Map<String, Object> config, String actor, boolean dryRun) {
		String taskId = required(config.get("taskId"), "流程任务 ID");
		boolean approved = bool(config.get("approved"), true);
		if (dryRun) return evidence("流程审批候选已生成", "taskId", taskId, "approved", approved,
				"comment", text(config.get("comment")));
		var instance = workflows.completeTask(taskId, approved, text(config.get("comment")), actor);
		return evidence("流程任务已处理", "instanceId", instance.getId(), "status", instance.getStatus());
	}

	private Map<String, Object> upsertGradeScheme(Map<String, Object> config, String actor, boolean dryRun) {
		Map<String, Object> scheme = new java.util.LinkedHashMap<>(payload(config, "scheme"));
		if (scheme.get("components") instanceof List<?>) {
			scheme.put("components", maps(scheme.get("components")).stream().map(item -> {
				Map<String, Object> component = new java.util.LinkedHashMap<>(item);
				String sourceType = text(component.get("sourceType"));
				if ("人工录入".equals(sourceType) || "手工录入".equals(sourceType)) {
					component.put("sourceType", "MANUAL");
				} else if ("考试同步".equals(sourceType)) {
					component.put("sourceType", "EXAM");
				}
				return component;
			}).toList());
		}
		SchemeCommand command = convert(scheme, SchemeCommand.class);
		String schemeId = text(config.get("schemeId"));
		if (dryRun) {
			grades.validateSchemeCandidate(command, actor);
			Map<String, String> componentIds = new java.util.LinkedHashMap<>();
			if (command.components() != null) {
				command.components().forEach(item -> {
					String code = required(item.code(), "成绩项目编码");
					componentIds.put(code, "CANDIDATE_GRADE_COMPONENT_" + code);
				});
			}
			return evidence("成绩方案候选已生成", "schemeId",
					blank(schemeId) ? "CANDIDATE_GRADE_SCHEME" : schemeId, "scheme", scheme,
					"componentIds", componentIds, "publish", bool(config.get("publish"), false));
		}
		var value = blank(schemeId) ? grades.createScheme(command, actor) : grades.updateScheme(schemeId, command, actor);
		if (bool(config.get("publish"), false)) value = grades.publishScheme(value.getId(), actor);
		Map<String, String> componentIds = new java.util.LinkedHashMap<>();
		grades.getScheme(value.getId(), actor).components().forEach(item -> componentIds.put(item.code(), item.id()));
		return evidence("成绩方案已保存", "schemeId", value.getId(), "status", value.getStatus(),
				"componentIds", componentIds);
	}

	private Map<String, Object> createGradebook(Map<String, Object> config, String actor, boolean dryRun) {
		GradebookCommand command = convert(payload(config, "gradebook"), GradebookCommand.class);
		if (dryRun) return evidence("成绩册创建候选已生成", "gradebookId", "CANDIDATE_GRADEBOOK",
				"offeringId", command.offeringId(), "schemeId", command.schemeId());
		var value = grades.createGradebook(command, actor);
		return evidence("成绩册已创建", "gradebookId", value.getId(), "status", value.getStatus(),
				"rowVersion", value.getRowVersion());
	}

	private Map<String, Object> saveGradeItems(Map<String, Object> config, String actor, boolean dryRun) {
		String gradebookId = required(config.get("gradebookId"), "成绩册 ID");
		ItemsCommand command = convert(payload(config, "itemsCommand"), ItemsCommand.class);
		if (dryRun) {
			return evidence("成绩录入候选已生成", "gradebookId", gradebookId,
					"itemCount", command.items() == null ? 0 : command.items().size(),
					"items", command.items());
		}
		var value = grades.saveItems(gradebookId, command, actor);
		return evidence("成绩已录入", "gradebookId", value.getId(), "status", value.getStatus(),
				"rowVersion", value.getRowVersion());
	}

	private Map<String, Object> syncExamGrades(Map<String, Object> config, String actor, boolean dryRun) {
		String gradebookId = required(config.get("gradebookId"), "成绩册 ID");
		if (dryRun) return evidence("考试成绩同步候选已生成", "gradebookId", gradebookId,
				"availableSessions", grades.availableExamSessions(
						grades.getGradebook(gradebookId, actor).offeringId(), actor));
		return evidence("考试成绩已同步", "gradebookId", gradebookId,
				"importedCount", grades.syncExamScores(gradebookId, actor));
	}

	private Map<String, Object> submitGrades(Map<String, Object> config, String actor, boolean dryRun) {
		String id = required(config.get("gradebookId"), "成绩册 ID");
		if (dryRun) return evidence("成绩册提交候选已核验", "gradebook", grades.getGradebook(id, actor));
		var value = grades.submit(id, actor);
		return evidence("成绩册已提交复核", "gradebookId", id, "status", value.getStatus(),
				"workflowInstanceId", value.getWorkflowInstanceId());
	}

	private Map<String, Object> checkGradeReview(Map<String, Object> config, String actor) {
		String id = required(config.get("gradebookId"), "成绩册 ID");
		String taskId = required(config.get("taskId"), "审核任务 ID");
		grades.checkReviewEligibility(id, taskId, actor);
		return evidence("当前账号通过成绩复核资格检查", "gradebookId", id, "taskId", taskId);
	}

	private Map<String, Object> reviewGrades(Map<String, Object> config, String actor, boolean dryRun) {
		String id = required(config.get("gradebookId"), "成绩册 ID");
		String taskId = required(config.get("taskId"), "审核任务 ID");
		boolean approved = bool(config.get("approved"), true);
		if (dryRun) return evidence("成绩复核候选已生成", "gradebookId", id, "taskId", taskId,
				"approved", approved, "gradebook", grades.getGradebook(id, actor));
		var value = grades.review(id, taskId, approved, text(config.get("comment")), actor);
		return evidence("成绩复核已处理", "gradebookId", id, "status", value.getStatus());
	}

	private Map<String, Object> publishGrades(Map<String, Object> config, String actor, boolean dryRun) {
		String id = required(config.get("gradebookId"), "成绩册 ID");
		if (dryRun) return evidence("成绩发布候选已核验", "gradebook", grades.getGradebook(id, actor));
		var value = grades.publish(id, actor);
		return evidence("成绩已发布", "gradebookId", id, "status", value.getStatus(),
				"publishedAt", String.valueOf(value.getPublishedAt()));
	}

	private Map<String, Object> requestGradeChange(Map<String, Object> config, String actor, boolean dryRun) {
		String id = required(config.get("gradebookId"), "成绩册 ID");
		ChangeRequestCommand command = convert(payload(config, "change"), ChangeRequestCommand.class);
		if (dryRun) return evidence("成绩更正候选已生成", "gradebookId", id, "change", payload(config, "change"));
		var value = gradeProduction.requestChange(id, command, actor);
		return evidence("成绩更正已提交审核", "requestId", value.getId(), "status", value.getStatus(),
				"workflowInstanceId", value.getWorkflowInstanceId());
	}

	private Map<String, Object> reviewGradeChange(Map<String, Object> config, String actor, boolean dryRun) {
		String requestId = required(config.get("requestId"), "成绩更正单 ID");
		ChangeDecisionCommand command = convert(payload(config, "decision"), ChangeDecisionCommand.class);
		if (dryRun) return evidence("成绩更正复核候选已生成", "requestId", requestId,
				"review", gradeProduction.reviewChange(requestId,
						required(config.get("instanceId"), "成绩更正流程实例 ID"), actor),
				"decision", payload(config, "decision"));
		var value = gradeProduction.decideChange(requestId, command, actor);
		return evidence("成绩更正复核已处理", "requestId", requestId, "status", value.getStatus());
	}

	private Map<String, Object> upsertExamPlan(Map<String, Object> config, boolean dryRun) {
		ExamCommands.Plan command = convert(payload(config, "plan"), ExamCommands.Plan.class);
		String planId = text(config.get("planId"));
		if (dryRun) return evidence("考试计划候选已生成", "planId",
				blank(planId) ? "CANDIDATE_EXAM_PLAN" : planId, "plan", payload(config, "plan"));
		var value = exams.savePlan(blank(planId) ? null : planId, command);
		return evidence("考试计划已保存", "planId", value.getId(), "status", value.getStatus());
	}

	private Map<String, Object> createExamSessions(Map<String, Object> config, boolean dryRun) {
		String planId = required(config.get("planId"), "考试计划 ID");
		List<Map<String, Object>> specs = maps(config.get("sessions"));
		if (specs.isEmpty()) throw new IllegalArgumentException("请提供考试场次");
		if (dryRun) return evidence("考试场次候选已生成", "planId", planId,
				"sessions", examPreviewRows(specs, "sessionId", "CANDIDATE_EXAM_SESSION_"));
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> spec : specs) {
			var value = exams.addSession(planId, convert(spec, ExamCommands.Session.class));
			result.add(evidence(null, "sessionId", value.getId(), "examDate", String.valueOf(value.getExamDate()),
					"startTime", String.valueOf(value.getStartTime())));
		}
		return evidence("考试场次已创建", "planId", planId, "sessions", result);
	}

	private Map<String, Object> createExamRooms(Map<String, Object> config, boolean dryRun) {
		List<Map<String, Object>> specs = maps(config.get("rooms"));
		if (specs.isEmpty()) throw new IllegalArgumentException("请提供考场配置");
		for (Map<String, Object> spec : specs) {
			required(spec.get("sessionId"), "考试场次 ID");
			required(spec.get("classroomId"), "考场对应的教室 ID");
		}
		if (dryRun) return evidence("考场配置候选已生成",
				"rooms", examPreviewRows(specs, "roomId", "CANDIDATE_EXAM_ROOM_"));
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> spec : specs) {
			String sessionId = required(spec.get("sessionId"), "考试场次 ID");
			Map<String, Object> roomCommand = new java.util.LinkedHashMap<>();
			roomCommand.put("classroomId", spec.get("classroomId"));
			roomCommand.put("requiredInvigilators", spec.get("requiredInvigilators"));
			var value = exams.addRoom(sessionId, convert(roomCommand, ExamCommands.Room.class));
			result.add(evidence(null, "roomId", value.getId(), "sessionId", sessionId,
					"classroomId", value.getClassroomId()));
		}
		return evidence("考场已创建", "rooms", result);
	}

	private Map<String, Object> assignExamCandidates(Map<String, Object> config, boolean dryRun) {
		List<Map<String, Object>> specs = maps(config.get("assignments"));
		if (specs.isEmpty()) throw new IllegalArgumentException("请提供考生分配配置");
		if (dryRun) return evidence("考生分配候选已生成", "assignments", specs);
		int count = 0;
		for (Map<String, Object> spec : specs) {
			String roomId = required(spec.get("roomId"), "考场 ID");
			List<String> studentIds = strings(spec.get("studentIds"));
			if (!studentIds.isEmpty()) count += exams.addCandidates(roomId, new ExamCommands.Candidates(studentIds)).size();
			for (String classId : strings(spec.get("administrativeClassIds"))) {
				count += exams.addClassCandidates(roomId, classId).size();
			}
		}
		return evidence("考生已分配", "assignedCount", count);
	}

	private Map<String, Object> autoAssignInvigilation(Map<String, Object> config, boolean dryRun) {
		String planId = required(config.get("planId"), "考试计划 ID");
		// A newly proposed plan is not in the database. Its actual conflict and
		// invigilator checks require the saved sessions, rooms and candidates.
		if (dryRun && "CANDIDATE_EXAM_PLAN".equals(planId)) {
			return evidence("待保存考试计划及考场后再核验监考，未执行监考分配",
					"planId", planId, "validationPending", true);
		}
		if (dryRun) return evidence("自动监考分配候选已核验", "planId", planId,
				"conflicts", exams.previewConflicts(planId));
		var assignments = exams.autoAssign(planId);
		return evidence("监考已自动分配", "planId", planId, "assignmentCount", assignments.size());
	}

	private List<Map<String, Object>> examPreviewRows(List<Map<String, Object>> specs, String idKey, String prefix) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> spec : specs) {
			Map<String, Object> row = new java.util.LinkedHashMap<>(spec);
			row.put(idKey, prefix + (result.size() + 1));
			result.add(row);
		}
		return result;
	}

	private Map<String, Object> examConflicts(Map<String, Object> config, boolean dryRun) {
		String planId = required(config.get("planId"), "考试计划 ID");
		if (dryRun && "CANDIDATE_EXAM_PLAN".equals(planId)) {
			return evidence("考试计划尚未保存，资源和课程冲突待正式核验",
					"planId", planId, "validationPending", true);
		}
		List<String> conflicts = exams.previewConflicts(planId);
		return evidence(conflicts.isEmpty() ? "考试计划无资源冲突" : "考试计划存在待处理冲突",
				"planId", planId, "conflictCount", conflicts.size(), "conflicts", conflicts);
	}

	private Map<String, Object> publishExamPlan(Map<String, Object> config, String actor, boolean dryRun) {
		String planId = required(config.get("planId"), "考试计划 ID");
		List<String> conflicts = exams.previewConflicts(planId);
		if (dryRun) return evidence("考试计划发布候选已核验", "planId", planId,
				"conflictCount", conflicts.size(), "conflicts", conflicts);
		var value = exams.publish(planId, actor);
		return evidence("考试计划已发布", "planId", planId, "status", value.getStatus(),
				"publishedVersion", value.getPublishedVersion());
	}

	private AdminUser requireUser(Map<String, Object> config) {
		String username = required(config.get("username"), "用户名");
		AdminUser user = users.findByUsername(username);
		if (user == null) throw new IllegalArgumentException("用户不存在：" + username);
		return user;
	}

	private void requireTargetUserOrganization(Map<String, Object> config, String actor, AdminUser user) {
		String organizationId = required(config.get("organizationId"), "目标机构");
		dataScopes.requireOrganization(authentication(actor), organizationId);
		if (!organizationId.equals(user.getOrganizationId()))
			throw new IllegalArgumentException("不能查询或调整其他机构用户的授权");
	}

	private UsernamePasswordAuthenticationToken authentication(String actor) {
		return new UsernamePasswordAuthenticationToken(actor, "", List.of());
	}

	private void validateFieldSpecs(List<Map<String, Object>> fields) {
		Set<String> keys = new LinkedHashSet<>();
		for (Map<String, Object> field : fields) {
			String key = required(field.get("fieldKey"), "字段编码");
			if (!keys.add(key)) throw new IllegalArgumentException("动态表单字段编码重复：" + key);
			required(field.get("fieldLabel"), "字段名称");
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> payload(Map<String, Object> config, String key) {
		Object value = config.get(key);
		if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
		return config;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	private List<Map<String, Object>> maps(Object value) {
		if (!(value instanceof List<?> list)) return List.of();
		return list.stream().filter(Map.class::isInstance).map(this::map).toList();
	}

	private List<String> strings(Object value) {
		if (!(value instanceof java.util.Collection<?> collection)) return List.of();
		return collection.stream().filter(java.util.Objects::nonNull).map(String::valueOf)
				.map(String::trim).filter(text -> !text.isEmpty()).distinct().toList();
	}

	private <T> T convert(Object value, Class<T> type) {
		try {
			return mapper.convertValue(value, type);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("业务参数格式无效：" + exception.getMessage(), exception);
		}
	}

	private String json(Object preferred, Object fallback) {
		Object value = preferred == null ? fallback : preferred;
		if (value == null) return null;
		if (value instanceof String text) return text;
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalArgumentException("字段选项不是有效 JSON", exception);
		}
	}

	private Map<String, Object> evidence(String message, Object... values) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (message != null) result.put("message", message);
		for (int index = 0; index + 1 < values.length; index += 2) {
			result.put(String.valueOf(values[index]), values[index + 1]);
		}
		return result;
	}

	private String required(Object value, String label) {
		String text = text(value);
		if (text.isBlank()) throw new IllegalArgumentException(label + "不能为空");
		return text;
	}

	private String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private boolean bool(Object value, boolean fallback) {
		return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
	}

	private int number(Object value, int fallback) {
		if (value instanceof Number number) return number.intValue();
		try {
			return value == null ? fallback : Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private int size(java.util.Collection<?> value) {
		return value == null ? 0 : value.size();
	}
}
