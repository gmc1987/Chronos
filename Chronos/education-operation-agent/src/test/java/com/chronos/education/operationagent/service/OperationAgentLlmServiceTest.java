package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
	private final OperationAgentLlmService service = new OperationAgentLlmService(
			models, new OperationAgentToolCatalog(), planningContext, new ObjectMapper());

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
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
			 {"stepName":"导入名册","toolCode":"admission.import.managed-file","toolConfig":{}}]}
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
	void interpretsNaturalLanguageConditionWithinSafeBounds() {
		when(models.chat(isNull(), anyString())).thenReturn("""
			{"admissionYear":2026,"batchLimit":900,"semesterCode":"2026-2027-1", 
			 "blockWriteTools":true,"rationale":"用户要求只读"}
			""");

		var result = service.interpretCondition("只读检查 2026 级，每批最多 900 人", "admin");

		assertThat(result).containsEntry("admissionYear", 2026)
				.containsEntry("batchLimit", 500)
				.containsEntry("blockWriteTools", true);
	}

	@Test
	void extractsControlledConditionWithoutWaitingForDeepSeek() {
		doThrow(new IllegalStateException("provider timeout")).when(models).chat(isNull(), anyString());

		var result = service.interpretCondition(
				"先暂停，只处理2026级和2026-2027-1，每批最多20人，本次不要写入正式数据", "admin");

		assertThat(result).containsEntry("admissionYear", 2026)
				.containsEntry("semesterCode", "2026-2027-1")
				.containsEntry("batchLimit", 20)
				.containsEntry("blockWriteTools", true);
		assertThat(result.get("llmRationale").toString()).contains("即时提取");
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
