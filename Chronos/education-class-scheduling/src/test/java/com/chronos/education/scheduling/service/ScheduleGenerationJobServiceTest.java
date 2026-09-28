package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionTemplate;

class ScheduleGenerationJobServiceTest {
	@Test
	void restartMarksInterruptedJobsFailedWithoutReplayingGeneration() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mock(ScheduleGenerationJobRepository.class);
		ScheduleGenerationJob queued = new ScheduleGenerationJob();
		queued.setStatus("QUEUED");
		ScheduleGenerationJob running = new ScheduleGenerationJob();
		running.setStatus("RUNNING");
		when(jobs.findByStatusIn(List.of("QUEUED", "RUNNING")))
				.thenReturn(List.of(queued, running));
		Executor executor = mock(Executor.class);
		var service = new ScheduleGenerationJobService(jobs, mock(AutoSchedulingService.class),
				mock(IAuditLogService.class), new TransactionTemplate(manager), executor);

		service.recoverInterruptedJobs();

		assertThat(List.of(queued, running)).allSatisfy(job -> {
			assertThat(job.getStatus()).isEqualTo("FAILED");
			assertThat(job.getFinishedAt()).isNotNull();
			assertThat(job.getErrorMessage()).contains("新建 AI Run", "核实已生成的候选");
		});
		verifyNoInteractions(executor);
	}

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

	@Test
	void repeatedAiSubmissionReturnsOnePersistedJobWithoutStartingTwice() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mock(ScheduleGenerationJobRepository.class);
		var saved = new java.util.concurrent.atomic.AtomicReference<ScheduleGenerationJob>();
		when(jobs.save(any())).thenAnswer(invocation -> {
			ScheduleGenerationJob value = invocation.getArgument(0);
			value.setId("job-1");
			saved.set(value);
			return value;
		});
		when(jobs.findByAgentRunId("run-1")).thenAnswer(invocation ->
				Optional.ofNullable(saved.get()));
		Executor executor = mock(Executor.class);
		var service = new ScheduleGenerationJobService(jobs, mock(AutoSchedulingService.class),
				mock(IAuditLogService.class), new TransactionTemplate(manager), executor);
		var command = new AutoScheduleCommand("2026-2027-1", "AI", "FULL", Set.of(),
				1, 5, 8, 1, 20);
		var constraints = new ScheduleRunConstraints(java.util.List.of(),
				java.util.List.of(new ScheduleRunConstraints.OfferingDuration("offering-1", 2)));
		var first = service.submit(command, "admin", "run-1", constraints);
		var repeated = service.submit(command, "admin", "run-1", constraints);
		assertThat(repeated.getId()).isEqualTo(first.getId());
		verify(jobs).save(any());
		verify(executor).execute(any());
		assertThatThrownBy(() -> service.submit(command, "different", "run-1", constraints))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void uniqueRunRaceReturnsCommittedJobWithoutDispatchingAnother() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mock(ScheduleGenerationJobRepository.class);
		ScheduleGenerationJob existing = new ScheduleGenerationJob();
		existing.setId("job-raced");
		existing.setSemesterCode("2026-2027-1");
		existing.setRequestedBy("admin");
		var command = new AutoScheduleCommand("2026-2027-1", "AI", "FULL", Set.of(),
				1, 5, 8, 1, 20);
		var constraints = ScheduleRunConstraints.empty();
		var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
		try {
			existing.setRequestJson(json.writeValueAsString(
					java.util.Map.of("command", command, "runConstraints", constraints)));
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new AssertionError(exception);
		}
		when(jobs.findByAgentRunId("run-1")).thenReturn(Optional.empty(), Optional.of(existing));
		when(jobs.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
		Executor executor = mock(Executor.class);
		var service = new ScheduleGenerationJobService(jobs, mock(AutoSchedulingService.class),
				mock(IAuditLogService.class), new TransactionTemplate(manager), executor);
		assertThat(service.submit(command, "admin", "run-1", constraints).getId()).isEqualTo("job-raced");
		verifyNoInteractions(executor);
	}
}
