package com.chronos.education.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.education.supervision.dao.*;
import com.chronos.education.supervision.model.*;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class SupervisionCenterServiceTest {
	@Mock SupervisionPlanRepository plans;
	@Mock SupervisionAssignmentRepository assignments;
	@Mock SupervisionRecordRepository records;
	@Mock SupervisionIssueRepository issues;
	@Mock SupervisionRectificationRepository rectifications;
	@Mock SupervisionReviewRepository reviews;
	@Mock EducationDataScopeService dataScopes;
	@Mock DomainEventOutboxService events;
	@Mock IAuditLogService audit;
	@InjectMocks SupervisionCenterService service;

	@Test
	void rejectsIllegalPlanTransition() {
		SupervisionPlan plan = new SupervisionPlan();
		plan.setSchoolId("ALL");
		plan.setStatus("DRAFT");
		when(plans.findById("p")).thenReturn(Optional.of(plan));
		when(dataScopes.resolve("admin")).thenReturn(new EducationDataScope(true,
				Set.of(), Set.of(), Set.of(), Set.of()));

		assertThatThrownBy(() -> service.startPlan("p", "admin"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void planCannotCompleteWhileRectificationIsOpen() {
		SupervisionPlan plan = new SupervisionPlan();
		plan.setSchoolId("ALL");
		plan.setStatus("IN_PROGRESS");
		SupervisionAssignment assignment = assignment("a", "supervisor");
		assignment.setStatus("COMPLETED");
		SupervisionRecord record = new SupervisionRecord();
		record.setId("r");
		SupervisionIssue issue = new SupervisionIssue();
		issue.setStatus("REVIEWING");
		when(plans.findById("p")).thenReturn(Optional.of(plan));
		when(dataScopes.resolve("admin")).thenReturn(new EducationDataScope(true,
				Set.of(), Set.of(), Set.of(), Set.of()));
		when(assignments.findByPlanId("p")).thenReturn(List.of(assignment));
		when(records.findByAssignmentId("a")).thenReturn(Optional.of(record));
		when(issues.findByRecordId("r")).thenReturn(List.of(issue));

		assertThatThrownBy(() -> service.completePlan("p", "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("督导计划仍有未关闭的整改问题");
		verify(plans, never()).save(any());
	}

	@Test
	void checkInFailsClosedWithoutTrustedProofProvider() {
		SupervisionAssignment assignment = assignment("a", "supervisor");
		when(assignments.findByIdAndSupervisorId("a", "supervisor")).thenReturn(Optional.of(assignment));

		assertThatThrownBy(() -> service.checkIn("a", "supervisor"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("SUPERVISION_CHECK_IN_PROVIDER_UNAVAILABLE");
		verify(audit).log("supervisor", "EDU_SUPERVISION_CHECK_IN_PROOF",
				"assignmentId=a,code=PROVIDER_UNAVAILABLE");
	}

	@Test
	void submissionIsImmutable() {
		SupervisionAssignment assignment = assignment("a", "supervisor");
		assignment.setStatus("CHECKED_IN");
		when(assignments.findByIdAndSupervisorId("a", "supervisor")).thenReturn(Optional.of(assignment));
		when(records.findByAssignmentId("a")).thenReturn(Optional.of(new SupervisionRecord()));

		assertThatThrownBy(() -> service.submit("a", "supervisor", "{}", "{}", "form"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("评价已提交且不可修改");
	}

	@Test
	void criticalIssueRequiresOwnerAndDeadline() {
		assertThatThrownBy(() -> service.createIssue("r", "admin", "CRITICAL", "title", "desc", null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("督导问题必须指定整改负责人账号");
		verifyNoInteractions(records);
	}

	@Test
	void rectificationOwnerCannotReviewOwnResult() {
		SupervisionIssue issue = new SupervisionIssue();
		issue.setSchoolId("ALL");
		issue.setStatus("REVIEWING");
		SupervisionRectification rectification = new SupervisionRectification();
		rectification.setRectifierId("owner");
		when(issues.findById("i")).thenReturn(Optional.of(issue));
		when(rectifications.findByIssueId("i")).thenReturn(Optional.of(rectification));
		when(dataScopes.resolve("owner")).thenReturn(new EducationDataScope(true,
				Set.of(), Set.of(), Set.of(), Set.of()));

		assertThatThrownBy(() -> service.review("i", "owner", true, ""))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(reviews);
	}

	@Test
	void overdueReminderUsesIndependentDomainOutbox() {
		SupervisionIssue issue = new SupervisionIssue();
		issue.setId("i");
		issue.setOwnerId("owner");
		when(issues.findByStatusAndDueAtBefore(eq("RECTIFYING"), any())).thenReturn(List.of(issue));

		assertThat(service.enqueueOverdueReminders(LocalDateTime.now())).isEqualTo(1);
		verify(events).enqueue(eq("EDU_SUPERVISION_ISSUE_OVERDUE"), eq("i"),
				eq("supervision-overdue:i"), any(SupervisionCenterService.OverdueIssueReminder.class));
	}

	private SupervisionAssignment assignment(String id, String supervisor) {
		SupervisionAssignment value = new SupervisionAssignment();
		value.setId(id);
		value.setSupervisorId(supervisor);
		value.setStatus("ACCEPTED");
		return value;
	}
}
