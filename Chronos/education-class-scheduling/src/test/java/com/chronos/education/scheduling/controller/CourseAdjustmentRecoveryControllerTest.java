package com.chronos.education.scheduling.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.model.CourseAdjustmentIncidentBatchCommand;
import com.chronos.education.scheduling.model.CourseAdjustmentIncidentBatchResult;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.CourseAdjustmentApplicationService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class CourseAdjustmentRecoveryControllerTest {
	@Test
	void batchRetryKeepsSuccessfulItemsWhenAnotherItemFails() {
		CourseAdjustmentApplicationService application = mock(
				CourseAdjustmentApplicationService.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("scheduler.admin");
		when(scopes.resolve("scheduler.admin")).thenReturn(fullAccess());

		CourseAdjustmentRecord applied = record("incident-1", "APPLIED", 1, "回写成功");
		CourseAdjustmentRecord failed = record("incident-2", "FAILED", 2, "教室冲突");
		IllegalStateException retryFailure = new IllegalStateException("教室冲突");
		when(application.retry("incident-1", "scheduler.admin")).thenReturn(applied);
		when(application.retry("incident-2", "scheduler.admin"))
				.thenThrow(retryFailure);
		when(application.recordRetryFailure(
				"incident-2",
				"scheduler.admin",
				retryFailure))
				.thenReturn(failed);

		CourseAdjustmentIncidentBatchResult result = new CourseAdjustmentRecoveryController(
				application,
				scopes).batchRetry(
						new CourseAdjustmentIncidentBatchCommand(List.of(
								"incident-1",
								"incident-1",
								"incident-2")),
						authentication).getData();

		assertEquals(2, result.requested());
		assertEquals(1, result.succeeded());
		assertEquals(1, result.failed());
		assertEquals(List.of("incident-1", "incident-2"), result.items().stream()
				.map(item -> item.id())
				.toList());
		verify(scopes).assertFullAccess(fullAccess());
	}

	@Test
	void batchRetryRejectsAnEmptySelection() {
		CourseAdjustmentApplicationService application = mock(
				CourseAdjustmentApplicationService.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("scheduler.admin");
		when(scopes.resolve("scheduler.admin")).thenReturn(fullAccess());

		CourseAdjustmentRecoveryController controller = new CourseAdjustmentRecoveryController(
				application,
				scopes);

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> controller.batchRetry(
						new CourseAdjustmentIncidentBatchCommand(List.of()),
						authentication));
		assertEquals("至少选择一条调课事故", exception.getMessage());
	}

	private CourseAdjustmentRecord record(
			String id,
			String status,
			int retryCount,
			String message) {
		CourseAdjustmentRecord record = new CourseAdjustmentRecord();
		record.setId(id);
		record.setStatus(status);
		record.setRetryCount(retryCount);
		record.setMessage(message);
		return record;
	}

	private EducationDataScope fullAccess() {
		return new EducationDataScope(
				true,
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of());
	}
}
