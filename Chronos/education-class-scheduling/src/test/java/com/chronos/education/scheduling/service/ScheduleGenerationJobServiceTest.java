package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.ScheduleGenerationJobRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.service.iService.IAuditLogService;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionTemplate;

class ScheduleGenerationJobServiceTest {
	@Test
	void nestedAiSubmissionWaitsForRunTransactionCommit() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		TransactionTemplate transactions = new TransactionTemplate(manager);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mock(ScheduleGenerationJobRepository.class);
		when(jobs.save(any())).thenAnswer(invocation -> {
			ScheduleGenerationJob job = invocation.getArgument(0);
			job.setId("job-1");
			return job;
		});
		Executor executor = mock(Executor.class);
		var service = new ScheduleGenerationJobService(jobs, mock(AutoSchedulingService.class),
				mock(IAuditLogService.class), transactions, executor);
		TransactionSynchronizationManager.initSynchronization();
		try {
			var command = new AutoScheduleCommand("2026-2027-1", "AI", "FULL", Set.of(),
					1, 5, 8, 1, 20);
			var constraints = new ScheduleRunConstraints(java.util.List.of(
					new ScheduleRunConstraints.TeacherSlot("teacher-1", 3, 3, "FORBIDDEN")));

			var job = service.submit(command, "admin", "run-1", constraints);

			assertThat(job.getId()).isEqualTo("job-1");
			assertThat(job.getRequestJson()).contains("teacher-1", "runConstraints");
			verifyNoInteractions(executor);
			TransactionSynchronizationUtils.triggerAfterCommit();
			verify(executor).execute(any());
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}
