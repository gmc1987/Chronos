package com.chronos.education.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.dao.TeachingReviewRecordRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.TeachingReviewRecord;
import com.chronos.education.scheduling.model.CourseOffering;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TeachingWorkflowTaskAccessGuardTest {
	@Test
	void requiresTheReviewersTeachingClassScope() {
		TeachingReviewRecordRepository records = mock(TeachingReviewRecordRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = mock(EducationDataScope.class);
		TeachingReviewRecord record = new TeachingReviewRecord();
		record.setOfferingId("other-school-offering");
		when(records.findByBusinessKey("teaching-review")).thenReturn(Optional.of(record));
		when(scopes.resolve("reviewer")).thenReturn(scope);
		doThrow(new AccessDeniedException("无权访问该教学班"))
				.when(scopes).assertOfferingAccess(scope, "other-school-offering");

		TeachingWorkflowTaskAccessGuard guard =
				new TeachingWorkflowTaskAccessGuard(records, offerings, scopes);
		assertThrows(AccessDeniedException.class,
				() -> guard.requireAccess("teaching-review", "reviewer"));
		verify(scopes).assertOfferingAccess(scope, "other-school-offering");
	}

	@Test
	void refusesReviewWithoutAnAuthoritativeRecord() {
		TeachingReviewRecordRepository records = mock(TeachingReviewRecordRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		when(records.findByBusinessKey("missing")).thenReturn(Optional.empty());
		TeachingWorkflowTaskAccessGuard guard =
				new TeachingWorkflowTaskAccessGuard(records, offerings, scopes);

		assertThrows(AccessDeniedException.class,
				() -> guard.requireAccess("missing", "reviewer"));
		verifyNoInteractions(scopes);
	}

	@Test
	void filtersCandidateTasksWithoutThrowingInsideTheListingTransaction() {
		TeachingReviewRecordRepository records = mock(TeachingReviewRecordRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = mock(EducationDataScope.class);
		TeachingReviewRecord record = new TeachingReviewRecord();
		record.setOfferingId("offering-1");
		CourseOffering offering = mock(CourseOffering.class);
		when(records.findByBusinessKey("teaching-review")).thenReturn(Optional.of(record));
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(scopes.resolve("reviewer")).thenReturn(scope);
		when(scopes.canAccessOffering(scope, offering)).thenReturn(true, false);

		TeachingWorkflowTaskAccessGuard guard =
				new TeachingWorkflowTaskAccessGuard(records, offerings, scopes);
		assertTrue(guard.canAccess("teaching-review", "reviewer"));
		assertFalse(guard.canAccess("teaching-review", "reviewer"));
	}
}
