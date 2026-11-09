package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.form.IFormDefinitionRepository;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.grade.service.GradeProductionService;
import com.chronos.education.scheduling.service.ExamCenterService;
import com.chronos.form.FormService;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.service.impl.AdminUserServiceImpl;
import com.chronos.service.impl.RoleServiceImpl;
import com.chronos.service.impl.UserRoleAssignmentService;
import com.chronos.workflow.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationAgentExtendedBusinessToolsTest {
	private final AdminUserServiceImpl userService = mock(AdminUserServiceImpl.class);
	private final RoleServiceImpl roleService = mock(RoleServiceImpl.class);
	private final UserRoleAssignmentService assignments = mock(UserRoleAssignmentService.class);
	private final IAdminUserRepository users = mock(IAdminUserRepository.class);
	private final FormService forms = mock(FormService.class);
	private final ExamCenterService exams = mock(ExamCenterService.class);
	private final OperationAgentExtendedBusinessTools tools = new OperationAgentExtendedBusinessTools(
			users, mock(IRoleRepository.class), userService, roleService, assignments,
			mock(IFormDefinitionRepository.class), forms, mock(WorkflowService.class),
			mock(GradeCenterService.class), mock(GradeProductionService.class), exams,
			new ObjectMapper(), mock(DirectoryDataScopeGuard.class));

	@Test
	void unsavedFormCanPreviewPublicationButCannotPublish() {
		var draft = tools.execute("form.definition.upsert", Map.of("dryRun", true, "form", Map.of(
				"code", "BORROW", "name", "借用单",
				"fields", List.of(Map.of("code", "reason", "name", "原因", "type", "text", "required", true)))), "admin");
		assertThat(draft).containsEntry("formKey", "BORROW").containsEntry("formName", "借用单");
		var preview = tools.execute("form.definition.publish", Map.of("dryRun", true, "formId", draft.get("formId")), "admin");
		assertThat(preview).containsEntry("status", "PENDING_FORM_CREATION");
		assertThatThrownBy(() -> tools.execute("form.definition.publish",
				Map.of("dryRun", false, "formId", draft.get("formId")), "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(forms);
	}

	@Test
	void newExamPreviewProvidesReferencesWithoutCheckingAnUnsavedPlanInTheDatabase() {
		var sessions = tools.execute("exam.sessions.create", Map.of("planId", "CANDIDATE_EXAM_PLAN",
				"sessions", List.of(Map.of("subjectId", "subject-1"))), "admin");
		assertThat(sessions).containsEntry("sessions",
				List.of(Map.of("subjectId", "subject-1", "sessionId", "CANDIDATE_EXAM_SESSION_1")));
		var rooms = tools.execute("exam.rooms.create", Map.of("rooms",
				List.of(Map.of("sessionId", "CANDIDATE_EXAM_SESSION_1", "classroomId", "room-1"))), "admin");
		assertThat(rooms).containsEntry("rooms", List.of(Map.of("sessionId", "CANDIDATE_EXAM_SESSION_1",
				"classroomId", "room-1", "roomId", "CANDIDATE_EXAM_ROOM_1")));
		var invigilation = tools.execute("exam.invigilation.auto",
				Map.of("planId", "CANDIDATE_EXAM_PLAN"), "admin");
		assertThat(invigilation).containsEntry("validationPending", true).doesNotContainKey("conflicts");
		verifyNoInteractions(exams);
	}

	@Test
	void formalUserUpsertEnsuresBaseRoleAsSeparateSource() {
		var saved = new com.chronos.model.pojo.AdminUser();
		saved.setId("new-id");
		saved.setUsername("new-user");
		saved.setOrganizationId("school-1");
		when(users.findByUsername("new-user")).thenReturn(null, saved);
		var result = tools.execute("iam.user.upsert", Map.of("organizationId", "school-1", "dryRun", false,
				"user", Map.of("username", "new-user", "password", "Temporary1107!", "status", 1)), "admin");
		assertThat(result).containsEntry("dryRun", false);
		verify(assignments).ensureBaseAccountRole(saved);
	}

	@Test
	void acceptsStructuredEnabledAliasWithoutWritingUser() {
		var result = tools.execute("iam.user.upsert", Map.of("organizationId", "school-1", "dryRun", true,
				"user", Map.of("username", "new-user", "enabled", true)), "admin");
		assertThat(result).containsEntry("username", "new-user").containsEntry("dryRun", true);
		verifyNoInteractions(userService);
	}

	@Test
	void previewCanReferenceMatchingPrecedingUserAndRoleCandidatesWithoutGrantingAnything() {
		var result = tools.execute("iam.authorization.assign", Map.of("organizationId", "school-1", "dryRun", true,
				"username", "new-user", "roleCodes", List.of("NEW_ROLE"), "userCandidate", "new-user",
				"userCandidateOnly", true, "roleCandidate", "NEW_ROLE", "roleCandidateOnly", true), "admin");
		assertThat(result).containsEntry("effective", false).containsEntry("userPendingCreation", true);
		verifyNoInteractions(assignments, userService, roleService);
	}

	@Test
	void mismatchedOrFormalReferencesNeverAllowUnknownUsers() {
		assertThatThrownBy(() -> tools.execute("iam.authorization.assign", Map.of("organizationId", "school-1",
				"dryRun", true, "username", "other-user", "roleCodes", List.of("NEW_ROLE"),
				"userCandidate", "new-user", "userCandidateOnly", true), "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> tools.execute("iam.authorization.assign", Map.of("organizationId", "school-1",
				"dryRun", false, "username", "new-user", "roleCodes", List.of("NEW_ROLE"),
				"userCandidate", "new-user", "userCandidateOnly", true), "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(assignments);
	}

	@Test
	void userProfileToolCannotBypassProtectedRoleAssignment() {
		assertThatThrownBy(() -> tools.execute("iam.user.upsert", Map.of("organizationId", "school-1",
				"dryRun", false, "user", Map.of("username", "new-user", "roleIds", List.of("admin-role"))), "admin"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能分配角色");
		verifyNoInteractions(userService, assignments);
	}
}
