package com.chronos.integration.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.model.SyncJob;

class IntegrationSchedulerTest {
	@Test
	void claimsAndReleasesAJobLeaseAroundDispatch() {
		SyncJobRepository jobs = mock(SyncJobRepository.class);
		IntegrationExtensionService extension = mock(IntegrationExtensionService.class);
		SyncJob job = new SyncJob();
		job.setId("job-1");
		when(jobs.findByStatus("ENABLED")).thenReturn(List.of(job));
		when(jobs.claimLease(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
				.thenReturn(1);

		new IntegrationScheduler(jobs, extension, 60_000L).dispatch();

		verify(extension).executeBatch("job-1", "integration-scheduler");
		verify(jobs).releaseLease(anyString(), anyString());
	}
}
