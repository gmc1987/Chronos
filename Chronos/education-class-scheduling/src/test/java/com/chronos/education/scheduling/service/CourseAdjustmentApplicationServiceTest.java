package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.education.scheduling.dao.CourseAdjustmentRecordRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.service.iService.IAuditLogService;

class CourseAdjustmentApplicationServiceTest {
	@Test
	void retryFailureDoesNotOverwriteConcurrentSuccessfulReplay() {
		CourseAdjustmentRecordRepository records = mock(CourseAdjustmentRecordRepository.class);
		CourseAdjustmentRecord applied = new CourseAdjustmentRecord();
		applied.setId("incident-1");
		applied.setStatus("APPLIED");
		applied.setRetryCount(1);
		when(records.findLockedById("incident-1")).thenReturn(Optional.of(applied));
		IAuditLogService audit = mock(IAuditLogService.class);
		CourseAdjustmentApplicationService service = new CourseAdjustmentApplicationService(
				mock(ScheduleEntryRepository.class),
				records,
				mock(ClassSchedulingService.class),
				mock(CourseAdjustmentStartValidator.class),
				mock(CourseAdjustmentIncidentNotificationService.class),
				audit);

		CourseAdjustmentRecord result = service.recordRetryFailure(
				"incident-1",
				"admin-b",
				new IllegalStateException("并发请求中的旧错误"));

		assertThat(result.getStatus()).isEqualTo("APPLIED");
		assertThat(result.getRetryCount()).isEqualTo(1);
		verify(records, never()).save(applied);
		verify(audit, never()).log(
				"admin-b",
				"EDUCATION_COURSE_ADJUSTMENT_RETRY_FAILED",
				"recordId=incident-1, error=并发请求中的旧错误");
	}
}
