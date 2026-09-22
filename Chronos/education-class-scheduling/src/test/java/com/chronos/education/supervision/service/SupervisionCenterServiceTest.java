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
		plan.setStatus("DRAFT");
		when(plans.findById("p")).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> service.startPlan("p", "admin"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void checkInUsesServerTimeAndRequiresAssignment() {
		SupervisionAssignment assignment = assignment("a", "supervisor");
		when(assignments.findByIdAndSupervisorId("a", "supervisor")).thenReturn(Optional.of(assignment));

		service.checkIn("a", "supervisor");

		assertThat(assignment.getCheckedInAt()).isNotNull();
		verify(audit).log("supervisor", "EDU_SUPERVISION_ASSIGNMENT_CHECK_IN", "assignmentId=a");
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
				.hasMessage("严重问题必须指定负责人和截止日期");
		verifyNoInteractions(records);
	}

	@Test
	void rectificationOwnerCannotReviewOwnResult() {
		SupervisionIssue issue = new SupervisionIssue();
		issue.setStatus("REVIEWING");
		SupervisionRectification rectification = new SupervisionRectification();
		rectification.setRectifierId("owner");
		when(issues.findById("i")).thenReturn(Optional.of(issue));
		when(rectifications.findByIssueId("i")).thenReturn(Optional.of(rectification));

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
