package com.chronos.education.supervision.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.EducationIdentityService;
import com.chronos.education.supervision.dao.*;
import com.chronos.education.supervision.model.SupervisionPlan;
import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class SupervisionCenterServiceScopeTest {
	@Test
	void revokedTeacherBindingHidesExistingAssignments() {
		EducationIdentityService identities = mock(EducationIdentityService.class);
		when(identities.teacherIds("former-supervisor")).thenReturn(java.util.Set.of());
		SupervisionAssignmentRepository assignments = mock(SupervisionAssignmentRepository.class);
		SupervisionCenterService service = new SupervisionCenterService(
				mock(SupervisionPlanRepository.class), assignments,
				mock(SupervisionRecordRepository.class), mock(SupervisionIssueRepository.class),
				mock(SupervisionRectificationRepository.class), mock(SupervisionReviewRepository.class),
				mock(EducationDataScopeService.class), identities,
				mock(DomainEventOutboxService.class), mock(IAuditLogService.class), null, null, null);

		assertThatThrownBy(() -> service.myAssignments("former-supervisor"))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(assignments);
	}

	@Test
	void rejectsSupervisorWithoutActiveTeacherBinding() {
		SupervisionPlanRepository plans = mock(SupervisionPlanRepository.class);
		SupervisionPlan plan = new SupervisionPlan();
		plan.setId("plan-1");
		plan.setSchoolId("ALL");
		plan.setStatus("PUBLISHED");
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		when(scopes.resolve("admin")).thenReturn(new EducationDataScope(true,
				java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));
		EducationIdentityService identities = mock(EducationIdentityService.class);
		when(identities.teacherIds("orphan-account")).thenReturn(java.util.Set.of());
		SupervisionAssignmentRepository assignments = mock(SupervisionAssignmentRepository.class);
		SupervisionCenterService service = new SupervisionCenterService(plans, assignments,
				mock(SupervisionRecordRepository.class), mock(SupervisionIssueRepository.class),
				mock(SupervisionRectificationRepository.class), mock(SupervisionReviewRepository.class),
				scopes, identities, mock(DomainEventOutboxService.class),
				mock(IAuditLogService.class), null, null, null);

		assertThatThrownBy(() -> service.createAssignment("admin", "plan-1",
				"orphan-account", "teacher", "entry", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("督导账号没有有效教师档案绑定");
		verifyNoInteractions(assignments);
	}

	@Test
	void schoolManagerCannotPublishOrAssignGlobalPlanById() {
		SupervisionPlanRepository plans = mock(SupervisionPlanRepository.class);
		SupervisionPlan plan = new SupervisionPlan();
		plan.setId("global-plan");
		plan.setSchoolId("ALL");
		plan.setStatus("DRAFT");
		when(plans.findById("global-plan")).thenReturn(Optional.of(plan));
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		when(scopes.resolve("manager")).thenReturn(new EducationDataScope(false,
				java.util.Set.of("campus-1"), java.util.Set.of(), java.util.Set.of(),
				java.util.Set.of()));
		SupervisionAssignmentRepository assignments = mock(SupervisionAssignmentRepository.class);
		SupervisionCenterService service = new SupervisionCenterService(plans, assignments,
				mock(SupervisionRecordRepository.class), mock(SupervisionIssueRepository.class),
				mock(SupervisionRectificationRepository.class),
				mock(DomainEventOutboxService.class), mock(IAuditLogService.class), scopes);

		assertThatThrownBy(() -> service.publishPlan("global-plan", "manager"))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.createAssignment("manager", "global-plan",
				"supervisor", "teacher", "entry", null))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(assignments);
	}

	@Test
	void missingPlanReturnsAClientErrorBeforeCreatingAssignment() {
		SupervisionPlanRepository plans = mock(SupervisionPlanRepository.class);
		SupervisionAssignmentRepository assignments = mock(SupervisionAssignmentRepository.class);
		when(plans.findById("missing")).thenReturn(Optional.empty());
		SupervisionCenterService service = new SupervisionCenterService(
				plans,
				assignments,
				mock(SupervisionRecordRepository.class),
				mock(SupervisionIssueRepository.class),
				mock(SupervisionRectificationRepository.class),
				mock(DomainEventOutboxService.class),
				mock(IAuditLogService.class),
				mock(EducationDataScopeService.class));

		assertThatThrownBy(() -> service.createAssignment(
				"manager", "missing", "supervisor", "teacher", "schedule", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("督导计划不存在");
		verifyNoInteractions(assignments);
	}

	@Test
	void rejectsAssignmentToAnotherCampusBeforePersisting() {
		SupervisionPlanRepository plans = mock(SupervisionPlanRepository.class);
		SupervisionPlan plan = new SupervisionPlan();
		plan.setId("plan-1");
		plan.setSchoolId("school-1");
		plan.setStatus("PUBLISHED");
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));

		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = new EducationDataScope(false, java.util.Set.of("campus-1"),
				java.util.Set.of(), java.util.Set.of(), java.util.Set.of("teacher-1"));
		when(scopes.resolve("manager")).thenReturn(scope);
		doNothing().when(scopes).assertSchoolAccess(scope, "school-1");
		doThrow(new AccessDeniedException("无权访问该校区督导数据"))
				.when(scopes).assertCampusAccess(scope, "campus-2");

		SupervisionCenterService service = new SupervisionCenterService(
				plans,
				mock(SupervisionAssignmentRepository.class),
				mock(SupervisionRecordRepository.class),
				mock(SupervisionIssueRepository.class),
				mock(SupervisionRectificationRepository.class),
				mock(DomainEventOutboxService.class),
				mock(IAuditLogService.class),
				scopes);

		assertThatThrownBy(() -> service.createAssignment(
				"manager", "plan-1", "teacher-1", "teacher-1", "schedule-1", "campus-2"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权访问该校区督导数据");
		verify(plans).findById("plan-1");
	}
}
