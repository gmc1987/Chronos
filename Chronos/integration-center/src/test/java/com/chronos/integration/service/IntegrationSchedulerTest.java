package com.chronos.integration.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.SyncJob;
import com.chronos.integration.model.SyncRun;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntegrationSchedulerTest {
	@Test
	void dispatchesOnlyJobsWithAnElapsedSchedule() {
		var jobs = mock(SyncJobRepository.class);
		var runs = mock(SyncRunRepository.class);
		var extension = mock(IntegrationExtensionService.class);
		var due = new SyncJob();
		due.setId("job-due");
		due.setCronExpression("0 * * * * *");
		due.setCreateTime(LocalDateTime.now().minusMinutes(2));
		var recent = new SyncJob();
		recent.setId("job-recent");
		recent.setCronExpression("0 0 12 * * *");
		var last = new SyncRun();
		last.setStartedAt(LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0));
		when(jobs.findByStatus("ENABLED")).thenReturn(List.of(due, recent));
		when(runs.findFirstByJobIdOrderByStartedAtDesc("job-recent")).thenReturn(Optional.of(last));

		new IntegrationScheduler(jobs, runs, extension).dispatch();

		verify(extension).executeBatch("job-due", "integration-scheduler");
		verify(extension, never()).executeBatch("job-recent", "integration-scheduler");
	}
}
