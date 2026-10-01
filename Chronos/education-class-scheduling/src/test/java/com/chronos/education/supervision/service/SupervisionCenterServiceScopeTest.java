package com.chronos.education.supervision.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.EducationDataScopeService;
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
