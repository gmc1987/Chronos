package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.education.operationagent.model.OperationAgentPlan;
import com.chronos.education.operationagent.model.OperationAgentStepRun;
import com.fasterxml.jackson.databind.ObjectMapper;

class OperationAgentLlmServiceTest {
	private final AiModelChatService models = mock(AiModelChatService.class);
	private final OperationAgentPlanningContextService planningContext = mock(OperationAgentPlanningContextService.class);
	private final OperationAgentManagedFileService managedFiles = mock(OperationAgentManagedFileService.class);
	private final OperationAgentLlmService service = new OperationAgentLlmService(
			models, new OperationAgentToolCatalog(), new OperationAgentSkillCatalog(), managedFiles,
			planningContext, new ObjectMapper());

	@BeforeEach
	void classifyLegacyTestFilesAsAdmissionRosters() {
		when(managedFiles.analyze(anyString(), any())).thenReturn(
				new OperationAgentManagedFileService.BusinessFileAnalysis(
						"admissions.xlsx", "ADMISSION_ROSTER", "新生录取名册",
						"admission.import.managed-file", 10, List.of("考生号*"), List.of(), true));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void modelInterpretsConversationAndSkillCompilesOnlyStructuredParameters() {
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"requiresSemester":true,"semesterCode":"2026-2027-2","createTerm":true,
			"confirmed":true,"skillCodes":["academic.term-setup"],"toolConfigs":{
			"academic.term.create":{"localTermCode":"2026-2027-2","academicYear":"2026-2027",
			"termNo":2,"startDate":"2026-09-01","endDate":"2027-01-30","weekCount":20},
			"academic.calendar.prepare":{},"academic.bell.configure":{}}}
			""");
		var auth = new UsernamePasswordAuthenticationToken("admin", "", List.of());
		var intent = service.recognizeConversation("帮我安排后半学年的校历和铃声\n日期已经告诉你了\n20", auth);
		var result = service.academicIntentDraft(intent, new OperationAgentLlmService.DraftCommand(
				"用户多轮目标", null, "2026-2027-2", null, null, List.of(), true, "school-1"));
		assertThat(result.modelGenerated()).isTrue();
		assertThat(result.plan().authorizedTools()).containsExactly(
				"academic.term.create", "academic.calendar.prepare", "academic.bell.configure");
		assertThat(result.requiredInputs()).extracting(OperationAgentLlmService.RequiredInput::key)
				.containsExactly("campusId", "scheduleName", "periods");
		assertThat(result.plan().steps().getFirst().toolConfig()).containsEntry("weekCount", 20);
	}

	@Test
	void recoversTermReferenceFromModelStructuredToolParameters() {
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"requiresSemester":true,"semesterCode":null,"createTerm":true,
			"skillCodes":["academic.term-setup"],"toolConfigs":{
			"academic.term.create":{"localTermCode":"2093-2094-1"}}}
			""");
		var intent = service.recognizeConversation("任意自然语言，由模型理解",
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));
		assertThat(intent.path("semesterCode").asText()).isEqualTo("2093-2094-1");
		String fullConversation = "长对话".repeat(800);
		var draft = service.academicIntentDraft(intent, new OperationAgentLlmService.DraftCommand(
				fullConversation, null, "2093-2094-1", null, null, List.of(), true, "school-1"));
		assertThat(draft.plan().objective()).hasSizeLessThan(1000);
		assertThat(draft.plan().ruleText()).contains(fullConversation);
	}

	@Test
	void conversationModelFailureNeverFallsBackToRegexExecution() {
		when(models.chat(isNull(), anyString())).thenThrow(new IllegalStateException("provider unavailable"));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.recognizeConversation(
				"创建学期2026-2027-2", new UsernamePasswordAuthenticationToken("admin", "", List.of())))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void generatesValidatedDraftFromRegisteredTools() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of("termExists", true));
		when(models.chat(isNull(), anyString())).thenReturn("""
			```json
			{"planName":"开学检查","objective":"检查准备度","ruleText":"只读检查","rationale":"先检查基础数据", 
			 "steps":[{"stepName":"检查学期","description":"核查上下文","toolCode":"semester.context.check","successCriteria":"返回统计","toolConfig":{}}]}
			```
			""");

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand("检查开学准备度", null, "2026-2027-1", null, null, true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.modelGenerated()).isTrue();
		assertThat(result.plan().authorizedTools()).containsExactly("semester.context.check");
		assertThat(result.plan().steps().getFirst().toolConfig())
				.containsEntry("semesterCode", "2026-2027-1");
		assertThat(result.readiness()).containsEntry("termExists", true);
		assertThat(result.readyToSave()).isTrue();
	}

	@Test
	void propagatesSelectedOrganizationIntoPlanningSnapshotAndEveryScopedTool() {
		when(planningContext.inspect("2026-2027-1", null, "school-1"))
				.thenReturn(Map.of("organizationId", "school-1", "termExists", true));
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"机构排课检查","objective":"只检查目标学校","ruleText":"不得跨校","steps":[
			 {"stepName":"检查准备度","toolCode":"teaching.readiness.check","toolConfig":{}},
			 {"stepName":"检索政策","toolCode":"knowledge.policy.search","toolConfig":{}}]}
			""");

		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				"检查目标学校排课准备度", null, "2026-2027-1", null, null, List.of(), true, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.readiness()).containsEntry("organizationId", "school-1");
		assertThat(result.plan().steps()).allSatisfy(step ->
				assertThat(step.toolConfig()).containsEntry("organizationId", "school-1"));
	}

	@Test
	void reordersGeneratedStepsByOpeningBusinessDependency() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"局部计划","objective":"先检查再分班","ruleText":"写入前审批","steps":[
			 {"stepName":"拟分班","toolCode":"admission.class.assign.batch","toolConfig":{}},
			 {"stepName":"检查学期","toolCode":"semester.context.check","toolConfig":{}},
			 {"stepName":"检查新生","toolCode":"admission.data.check","toolConfig":{}}]}
			""");

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand("检查后分班", null, "2026-2027-1", null, null, false),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("semester.context.check", "admission.data.check", "admission.class.assign.batch");
	}

	@Test
	void insertsUploadedRosterOnServerBeforeAdmissionCheckAndClassAssignment() {
		when(planningContext.inspect("2026-2027-1", 2026)).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"新生入学","objective":"导入并拟分班","ruleText":"先形成候选结果","steps":[
			 {"stepName":"导入名册","toolCode":"admission.import.managed-file","toolConfig":{}},
			 {"stepName":"拟分班","toolCode":"admission.class.assign.batch","toolConfig":{}},
			 {"stepName":"检查新生","toolCode":"admission.data.check","toolConfig":{}}]}
			""");

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"导入名册并拟分班", null, "2026-2027-1", 2026, "managed-file-1", true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("admission.import.managed-file", "admission.data.check", "admission.class.assign.batch");
		var importStep = result.plan().steps().getFirst();
		assertThat(importStep.approvalRequired()).isFalse();
		assertThat(importStep.toolConfig())
				.containsEntry("managedFileId", "managed-file-1")
				.containsEntry("dryRun", true);
	}

	@Test
	void completesExplicitAccountProvisioningGoalEvenWhenModelOmitsLaterSteps() {
		when(planningContext.inspect("2026-2027-1", 2026)).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"新生入学","objective":"导入新生","ruleText":"生成候选后确认","steps":[
			 {"stepName":"导入名册","toolCode":"admission.import.managed-file","toolConfig":{}},
			 {"stepName":"业务步骤","toolCode":"admission.data.check","toolConfig":{}},
			 {"stepName":"业务步骤","toolCode":"admission.class.assign.batch","toolConfig":{}},
			 {"stepName":"业务步骤","toolCode":"admission.review.batch","toolConfig":{}},
			 {"stepName":"业务步骤","toolCode":"admission.enroll.batch","toolConfig":{}},
			 {"stepName":"业务步骤","toolCode":"student.account.provision.batch","toolConfig":{}},
			 {"stepName":"业务步骤","toolCode":"account.binding.check","toolConfig":{}}]}
			""");

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"导入新生名册并完成学生账号生成和账号绑定", null,
						"2026-2027-1", 2026, "managed-file-1", true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.modelGenerated()).isTrue();
		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly(
						"admission.import.managed-file", "admission.data.check",
						"admission.class.assign.batch", "admission.review.batch",
						"admission.enroll.batch", "student.account.provision.batch",
						"account.binding.check");
	}

	@Test
	void reportsMissingBusinessInputInsteadOfRejectingDraft() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"教师分工","objective":"生成教师分工","ruleText":"用户确认后写入","steps":[
			 {"stepName":"生成教师分工","toolCode":"teaching.assignment.generate","toolConfig":{}}]}
			""");

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand("生成教师分工", null, "2026-2027-1", null, null, true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.readyToSave()).isFalse();
		assertThat(result.requiredInputs()).extracting(OperationAgentLlmService.RequiredInput::key)
				.containsExactly("assignments");
		assertThat(result.plan().steps().getFirst().toolConfig()).containsEntry("dryRun", true);
	}

	@Test
	void composesMultipleBusinessSkillsFromServerClassifiedAttachments() {
		when(planningContext.inspect(null, null)).thenReturn(Map.of());
		when(managedFiles.analyze(eq("employee-file"), any())).thenReturn(
				new OperationAgentManagedFileService.BusinessFileAnalysis(
						"04-employees.xlsx", "EMPLOYEE_DIRECTORY", "教职工与任职数据",
						"iam.employee.import.managed-file", 175, List.of("导入标识*"), List.of(), true));
		when(managedFiles.analyze(eq("department-file"), any())).thenReturn(
				new OperationAgentManagedFileService.BusinessFileAnalysis(
						"05-leaders.xlsx", "DEPARTMENT_DIRECTORY", "组织部门与负责人数据",
						"iam.department.import.managed-file", 48, List.of("部门编码*"), List.of(), true));
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		// The model response is intentionally unusable. The deterministic planner
		// must still route both trusted files to two different business tools.
		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				"导入教职工并回填部门负责人", null, null, null, null,
				List.of("employee-file", "department-file"), true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactlyInAnyOrder("iam.employee.import.managed-file", "iam.department.import.managed-file");
		assertThat(result.plan().steps().stream()
				.filter(step -> "iam.employee.import.managed-file".equals(step.toolCode()))
				.findFirst().orElseThrow().toolConfig())
				.containsEntry("managedFileId", "employee-file")
				.containsEntry("syncAccounts", true);
	}

	@Test
	void reusesExistingCourseOfferingsForSchedulingInsteadOfRequestingTeacherMappings() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of(
				"courseOfferingCount", 531L,
				"teachingAssignmentCount", 3L));
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"排课候选","objective":"生成全量候选","ruleText":"只生成候选","steps":[
			 {"stepName":"检查排课准备度","toolCode":"teaching.readiness.check","toolConfig":{}},
			 {"stepName":"生成排课候选","toolCode":"scheduling.generate.full","toolConfig":{}}]}
			""");

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"检查当前学期基础数据并生成全量排课预选方案",
						null, "2026-2027-1", null, null, true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.readyToSave()).isTrue();
		assertThat(result.requiredInputs()).isEmpty();
		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("teaching.readiness.check", "scheduling.generate.full");
	}

	@Test
	void authorizationCandidateReferencesPrecedingCreationsAndVerifiesAfterGrant() {
		when(planningContext.inspect(null, null, "school-1")).thenReturn(Map.of("organizationId", "school-1"));
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"用户角色候选","objective":"创建及授权","ruleText":"先预览","steps":[
			{"stepName":"核验","toolCode":"iam.authorization.check","toolConfig":{"username":"new-user"}},
			{"stepName":"用户","toolCode":"iam.user.upsert","toolConfig":{"user":{"username":"new-user"}}},
			{"stepName":"角色","toolCode":"iam.role.upsert","toolConfig":{"role":{"roleCode":"NEW_ROLE","roleName":"新角色"}}},
			{"stepName":"授权","toolCode":"iam.authorization.assign","toolConfig":{"username":"new-user","roleCodes":["NEW_ROLE"]}}]}
			""");
		var draft = service.draft(new OperationAgentLlmService.DraftCommand("用户目标", null, null,
				null, null, List.of(), true, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));
		assertThat(draft.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("iam.user.upsert", "iam.role.upsert", "iam.authorization.assign", "iam.authorization.check");
		assertThat(draft.plan().steps().get(2).toolConfig())
				.containsEntry("userCandidateOnly", "${iam.user.upsert.dryRun}")
				.containsEntry("roleCandidateOnly", "${iam.role.upsert.dryRun}");
		assertThat(draft.plan().steps().getLast().toolConfig())
				.containsEntry("proposedRoleCodes", "${iam.authorization.assign.roleCodes}");
	}

	@Test
	void setsFormalWriteModeWhenModelOmitsDryRun() {
		when(planningContext.inspect(null, null)).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
				{"planName":"发起流程","objective":"正式办理","ruleText":"确认后写入","steps":[
				{"stepName":"发起","toolCode":"workflow.instance.start","toolConfig":{"flowId":"flow-1","formData":{"resourceId":"demo"}}}]}
				""");
		var actor = new UsernamePasswordAuthenticationToken("admin", "", List.of());
		for (boolean candidateOnly : List.of(false, true)) {
			var draft = service.draft(new OperationAgentLlmService.DraftCommand("办理测试流程", null,
					null, null, null, candidateOnly), actor);
			assertThat(draft.plan().steps().getFirst().toolConfig()).containsEntry("dryRun", candidateOnly);
		}
	}

	@Test
	void submittingToWorkflowDoesNotScheduleApprovalUsingNonexistentTaskOutput() {
		when(planningContext.inspect(null, null)).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
				{"planName":"发起流程","objective":"交独立审核","ruleText":"本人确认发起",
				"requiredTools":["workflow.instance.start","workflow.task.complete"],"steps":[
				{"stepName":"发起","toolCode":"workflow.instance.start","toolConfig":{"flowId":"flow-1","formData":{"resourceId":"demo"}}},
				{"stepName":"审批","toolCode":"workflow.task.complete","toolConfig":{"taskId":"${workflow.instance.start.taskId}"}}]}
				""");
		var draft = service.draft(new OperationAgentLlmService.DraftCommand("发起流程并交独立审核", null,
				null, null, null, false), new UsernamePasswordAuthenticationToken("admin", "", List.of()));
		assertThat(draft.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("workflow.instance.start");
		assertThat(draft.plan().authorizedTools()).containsExactly("workflow.instance.start");
	}

	@Test
	void workflowPlanningUsesActorAuthorizedFormSchema() throws Exception {
		when(planningContext.inspect(null, null)).thenReturn(Map.of());
		when(planningContext.availableWorkflowSchemas("admin", "教学内容审核")).thenReturn(List.of(
				Map.of("id", "flow-1", "fields", List.of(Map.of("fieldKey", "resourceId", "fieldLabel", "资源编号")))));
		when(models.chat(isNull(), org.mockito.ArgumentMatchers.contains("resourceId"))).thenReturn("""
				{"planName":"流程候选","objective":"预览流程","ruleText":"只读","steps":[
				{"stepName":"预览","toolCode":"workflow.instance.start","toolConfig":{"flowId":"flow-1","formData":{"resourceId":"demo"}}}]}
				""");
		var intent = new ObjectMapper().readTree("""
				{"skillCodes":["workflow.dynamic-form-operation"],"workflowReference":"教学内容审核"}
				""");
		var draft = service.draft(new OperationAgentLlmService.DraftCommand("预览教学内容审核", null,
				null, null, null, true), new UsernamePasswordAuthenticationToken("admin", "", List.of()), intent);
		assertThat(draft.modelGenerated()).isTrue();
		assertThat(draft.plan().steps().getFirst().toolConfig())
				.containsEntry("flowId", "flow-1").containsEntry("formData", Map.of("resourceId", "demo"));
	}

	@Test
	void structuredFlatGradeParametersAreWrappedAndNewSchemeIsReferenced() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of("termExists", true));
		when(models.chat(isNull(), anyString())).thenReturn("""
				{"planName":"成绩候选","objective":"方案和成绩册","ruleText":"只读","steps":[
				{"stepName":"方案","toolCode":"grade.scheme.upsert","toolConfig":{"name":"总评","offeringId":"offering-1","totalScore":100,"passScore":60}},
				{"stepName":"建册","toolCode":"grade.gradebook.create","toolConfig":{"offeringId":"offering-1","schemeId":"guessed-id"}}]}
				""");
		var draft = service.draft(new OperationAgentLlmService.DraftCommand("仅验收候选", null,
				"2026-2027-1", null, null, true), new UsernamePasswordAuthenticationToken("admin", "", List.of()));
		assertThat(draft.plan().steps().getFirst().toolConfig()).containsEntry("scheme",
				Map.of("name", "总评", "offeringId", "offering-1", "totalScore", 100, "passScore", 60));
		assertThat(draft.plan().steps().getFirst().toolConfig()).containsEntry("publish", true);
		assertThat(draft.plan().steps().getLast().toolConfig()).containsEntry("gradebook",
				Map.of("offeringId", "offering-1", "schemeId", "${grade.scheme.upsert.schemeId}"));
	}

	@Test
	void candidateCoverageExcludesFormalSubmissionButKeepsReadOnlyExamCheck() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of("termExists", true));
		when(models.chat(isNull(), anyString())).thenReturn("""
				{"planName":"候选","objective":"成绩候选与考试检查","ruleText":"只读",
				"requiredTools":["grade.submit","exam.conflicts.check"],"steps":[
				{"stepName":"提交","toolCode":"grade.submit","toolConfig":{}},
				{"stepName":"检查考试","toolCode":"exam.conflicts.check","toolConfig":{"planId":"existing-exam"}}]}
				""");
		var draft = service.draft(new OperationAgentLlmService.DraftCommand("仅验收候选", null,
				"2026-2027-1", null, null, true), new UsernamePasswordAuthenticationToken("admin", "", List.of()));
		assertThat(draft.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("exam.conflicts.check");
		assertThat(draft.plan().steps().getFirst().toolConfig()).containsEntry("planId", "existing-exam");
	}

	@Test
	void modelAdmissionPlanCarriesValidatedRosterScopeIntoSubsequentWrites() {
		when(planningContext.inspect("2026-2027-1", 2026)).thenReturn(Map.of("termExists", true));
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"名册办理","objective":"本附件入学","ruleText":"仅本附件","requiredTools":["admission.enroll.batch"],"steps":[
			{"stepName":"导入","toolCode":"admission.import.managed-file","toolConfig":{}},
			{"stepName":"复查","toolCode":"admission.review.batch","toolConfig":{}},
			{"stepName":"建档","toolCode":"admission.enroll.batch","toolConfig":{}}]}
			""");
		var draft = service.draft(new OperationAgentLlmService.DraftCommand("只办理名册学生", null,
				"2026-2027-1", 2026, "roster-file", true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));
		assertThat(draft.plan().steps()).filteredOn(step -> !step.toolCode().equals("admission.import.managed-file"))
				.allSatisfy(step -> assertThat(step.toolConfig())
						.containsEntry("candidateNos", "${admission.import.managed-file.candidateNos}")
						.containsEntry("importRows", "${admission.import.managed-file.rows}")
						.containsEntry("dryRun", true));
	}

	@Test
	void fallsBackToExecutableControlledPlanWhenDeepSeekIsUnavailable() {
		when(planningContext.inspect("2026-2027-1", 2026)).thenReturn(Map.of("termExists", true));
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"导入新生名册，完成分班、建档、学号和学生账号生成",
						null, "2026-2027-1", 2026, "managed-file-1", true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.modelGenerated()).isFalse();
		assertThat(result.readyToSave()).isTrue();
		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly(
						"admission.import.managed-file", "admission.data.check",
						"admission.class.assign.batch", "admission.review.batch",
						"admission.enroll.batch", "student.account.provision.batch",
						"account.binding.check");
		assertThat(result.plan().steps()).filteredOn(step -> step.toolConfig().containsKey("dryRun"))
				.allMatch(step -> Boolean.TRUE.equals(step.toolConfig().get("dryRun")))
				.allMatch(step -> !Boolean.TRUE.equals(step.approvalRequired()));
		assertThat(result.warnings().getFirst()).contains("受控业务规划器");
	}

	@Test
	void fallbackKeepsCompleteAdmissionChainForAdministrativeClassWording() {
		when(planningContext.inspect("2026-2027-1", 2026)).thenReturn(Map.of("termExists", true));
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"导入新生名册，完成行政班分配、正式建档、学生账号生成和账号绑定",
						null, "2026-2027-1", 2026, "managed-file-1", true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly(
						"admission.import.managed-file", "admission.data.check",
						"admission.class.assign.batch", "admission.review.batch",
						"admission.enroll.batch", "student.account.provision.batch",
						"account.binding.check");
	}

	@Test
	void fallbackReportsMissingRosterInsteadOfFailingWhenNoFileWasUploaded() {
		when(planningContext.inspect(null, 2026)).thenReturn(Map.of("termExists", true));
		doThrow(new IllegalStateException("truncated model json")).when(models).chat(isNull(), anyString());

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"读取新生名册并完成正式建档、学生账号生成和账号绑定",
						null, null, 2026, null, true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.modelGenerated()).isFalse();
		assertThat(result.readyToSave()).isFalse();
		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly(
						"admission.import.managed-file", "admission.data.check",
						"admission.class.assign.batch", "admission.review.batch",
						"admission.enroll.batch", "student.account.provision.batch",
						"account.binding.check");
		assertThat(result.requiredInputs())
				.anyMatch(input -> "managedFileId".equals(input.key()));
	}

	@Test
	void fallbackBuildsReviewableExamPlanCandidateWithoutPublishing() {
		when(planningContext.inspect("2026-2027-1", null)).thenReturn(Map.of("termExists", true));
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		var result = service.draft(
				new OperationAgentLlmService.DraftCommand(
						"创建当前学期完整考试计划，安排场次、考场、考生和监考",
						null, "2026-2027-1", null, null, true),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("exam.plan.upsert", "exam.sessions.create", "exam.rooms.create",
						"exam.candidates.assign", "exam.invigilation.auto", "exam.conflicts.check");
		assertThat(result.plan().steps()).noneMatch(step -> "exam.plan.publish".equals(step.toolCode()));
		assertThat(result.plan().steps().stream()
				.filter(step -> "exam.sessions.create".equals(step.toolCode())).findFirst().orElseThrow().toolConfig())
				.containsEntry("planId", "${exam.plan.upsert.planId}");
	}

	@Test
	void extractsCompleteAcademicTermCandidateFromNaturalLanguage() {
		when(planningContext.inspect("SCHOOL:2097-2098-1", null, "school-1")).thenReturn(Map.of());
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"planName":"创建学期","objective":"创建新学期","ruleText":"确认后写入","steps":[
			 {"stepName":"创建学期","toolCode":"academic.term.create","toolConfig":{"localTermCode":"2097-2098-1","academicYear":"2097-2098","termNo":1,"termName":"2097-2098学年第一学期","startDate":"2097-09-01","endDate":"2098-01-20","weekCount":20,"currentTerm":true}}]}
			""");

		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				"创建 2097-2098 学年第一学期，从2097-09-01到2098-01-20，共20个教学周，并设为当前学期",
				null, "SCHOOL:2097-2098-1", null, null, List.of(), false, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.readyToSave()).isTrue();
		assertThat(result.requiredInputs()).isEmpty();
		assertThat(result.plan().steps()).hasSize(1);
		var step = result.plan().steps().getFirst();
		assertThat(step.toolCode()).isEqualTo("academic.term.create");
		assertThat(step.approvalRequired()).isTrue();
		assertThat(step.toolConfig())
				.containsEntry("organizationId", "school-1")
				.containsEntry("localTermCode", "2097-2098-1")
				.containsEntry("academicYear", "2097-2098")
				.containsEntry("termNo", 1)
				.containsEntry("termName", "2097-2098学年第一学期")
				.containsEntry("startDate", "2097-09-01")
				.containsEntry("endDate", "2098-01-20")
				.containsEntry("weekCount", 20)
				.containsEntry("currentTerm", true);
	}

	@Test
	void deterministicPlannerKeepsTermCreationAndReportsMissingDates() {
		when(planningContext.inspect("SCHOOL:2097-2098-2", null, "school-1")).thenReturn(Map.of());
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				"新建2097-2098学年第二学期", null, "SCHOOL:2097-2098-2",
				null, null, List.of(), false, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.modelGenerated()).isFalse();
		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.containsExactly("academic.term.create");
		assertThat(result.requiredInputs()).extracting(OperationAgentLlmService.RequiredInput::key)
				.containsExactly("startDate", "endDate", "weekCount");
	}

	@Test
	void accumulatesTermFieldsFromTheActualMultiTurnConversation() {
		when(planningContext.inspect("SCHOOL:2026-2027-2", null, "school-1")).thenReturn(Map.of());
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		String conversation = """
			请帮我创建2026年度第二学期，同时完成教学日历和校区作息方案
			用户补充：2026-2027学年第二学期
			用户补充：是的
			用户补充：开始日期：2026-09-01；结束日期：2027-01-30；教学周数：20
			""";
		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				conversation, null, "SCHOOL:2026-2027-2", null, null, List.of(), false, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.requiredInputs()).extracting(OperationAgentLlmService.RequiredInput::key)
				.containsExactly("campusId", "scheduleName", "periods");
		assertThat(result.readyToSave()).isFalse();
		assertThat(result.plan().steps()).extracting(OperationAgentService.StepCommand::toolCode)
				.contains("academic.term.create", "academic.calendar.prepare", "academic.bell.configure");
		assertThat(result.plan().steps().stream()
				.filter(step -> "academic.term.create".equals(step.toolCode()))
				.findFirst().orElseThrow().toolConfig())
				.containsEntry("startDate", "2026-09-01")
				.containsEntry("endDate", "2027-01-30")
				.containsEntry("weekCount", 20);
	}

	@Test
	void acceptsCompactSemicolonTermDetailsFromFollowUp() {
		when(planningContext.inspect("SCHOOL:2026-2027-2", null, "school-1")).thenReturn(Map.of());
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				"创建学期 2026-2027-2\n用户补充：2026-09-01;2027-01-30;20",
				null, "SCHOOL:2026-2027-2", null, null, List.of(), false, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.requiredInputs()).isEmpty();
		assertThat(result.plan().steps().stream()
				.filter(step -> "academic.term.create".equals(step.toolCode()))
				.findFirst().orElseThrow().toolConfig())
				.containsEntry("startDate", "2026-09-01")
				.containsEntry("endDate", "2027-01-30")
				.containsEntry("weekCount", 20);
	}

	@Test
	void retainsDatesAndAcceptsBareWeekCountFromTheNextTurn() {
		when(planningContext.inspect("SCHOOL:2026-2027-2", null, "school-1")).thenReturn(Map.of());
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		String conversation = """
			创建学期 2026-2027-2
			用户补充：开始日期：2026-09-01；结束日期：2027-01-30
			用户补充：20
			""";
		var result = service.draft(new OperationAgentLlmService.DraftCommand(
				conversation, null, "SCHOOL:2026-2027-2", null, null, List.of(), false, "school-1"),
				new UsernamePasswordAuthenticationToken("admin", "", List.of()));

		assertThat(result.requiredInputs()).isEmpty();
		assertThat(result.plan().steps().getFirst().toolConfig())
				.containsEntry("startDate", "2026-09-01")
				.containsEntry("endDate", "2027-01-30")
				.containsEntry("weekCount", 20);
	}

	@Test
	void interpretsNaturalLanguageConditionWithinSafeBounds() {
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"admissionYear":2026,"batchLimit":500,"semesterCode":"2026-2027-1",
			 "blockWriteTools":true,"rationale":"用户要求只读"}
			""");

		var result = service.interpretCondition("只读检查 2026 级，每批最多 900 人", "admin");

		assertThat(result).containsEntry("admissionYear", 2026)
				.containsEntry("batchLimit", 500)
				.containsEntry("blockWriteTools", true);
	}

	@Test
	void conditionInterpretationRequiresModelAndDoesNotSilentlyUseKeywords() {
		when(models.chat(isNull(), anyString())).thenThrow(new IllegalStateException("provider timeout"));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.interpretCondition(
				"只读检查2026级，每批20人", "admin")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void acceptsOnlyControlledStepDecisions() {
		when(models.chat(isNull(), anyString())).thenReturn(
				"{\"decision\":\"REQUIRE_HUMAN\",\"rationale\":\"涉及正式数据\",\"nextAction\":\"请审核\"}");
		OperationAgentPlan plan = new OperationAgentPlan();
		plan.setObjective("准备新学期");
		plan.setRuleText("写入前审批");
		OperationAgentStepRun step = new OperationAgentStepRun();
		step.setStepName("发布课表");

		var decision = service.assessStep(plan, step,
				new OperationAgentToolCatalog().require("scheduling.publish"), Map.of(), "admin");

		assertThat(decision.decision()).isEqualTo("REQUIRE_HUMAN");
		assertThat(decision.nextAction()).isEqualTo("请审核");
	}

	@Test
	void fallsBackToPlatformApprovalPolicyWhenStepDecisionModelIsUnavailable() {
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());
		OperationAgentPlan plan = new OperationAgentPlan();
		plan.setObjective("正式分班");
		plan.setRuleText("写入前审批");
		OperationAgentStepRun step = new OperationAgentStepRun();
		step.setStepName("应用分班结果");

		var formal = service.assessStep(plan, step,
				new OperationAgentToolCatalog().require("admission.class.assign.batch"),
				Map.of("dryRun", false), "admin");
		var candidate = service.assessStep(plan, step,
				new OperationAgentToolCatalog().require("admission.class.assign.batch"),
				Map.of("dryRun", true), "admin");

		assertThat(formal.decision()).isEqualTo("REQUIRE_HUMAN");
		assertThat(candidate.decision()).isEqualTo("PROCEED");
	}
}
