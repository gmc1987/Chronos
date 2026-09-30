package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.ScheduleGenerationJobRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;

class ScheduleGenerationJobServiceTest {
	@ParameterizedTest
	@MethodSource("aiOnlyRules")
	void nonAiSubmissionRejectsEachNewAiRule(ScheduleRunConstraints constraints) {
		var fixture = fixture();

		assertThatThrownBy(() -> fixture.service()
				.submit(command(), "admin", null, constraints))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("只能由 AI Run 提交");
		verify(fixture.jobs(), never()).save(any());
		verifyNoInteractions(fixture.scheduling());
	}

	private static List<ScheduleRunConstraints> aiOnlyRules() {
		return List.of(
				new ScheduleRunConstraints(List.of(), List.of(),
						List.of(new ScheduleRunConstraints.WeekRule("offering-1", "ODD", 1, 20)),
						List.of(), List.of()),
				new ScheduleRunConstraints(List.of(), List.of(), List.of(),
						List.of(new ScheduleRunConstraints.LockedEntry("entry-1", "offering-1", 1, 1)),
						List.of()),
				new ScheduleRunConstraints(List.of(), List.of(), List.of(), List.of(),
						List.of(new ScheduleRunConstraints.SoftPriority("TEACHER_GAP", "teacher-1"))));
	}

	@Test
	void recoveryOnlyFailsExpiredJobsWithConditionalUpdateWithoutReplayingGeneration() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mockJobs();
		ScheduleGenerationJob queued = new ScheduleGenerationJob();
		queued.setStatus("QUEUED");
		queued.setId("expired");
		ScheduleGenerationJob running = new ScheduleGenerationJob();
		running.setStatus("RUNNING");
		running.setId("raced");
		running.setResultCandidateIds("[\"candidate-1\"]");
		when(jobs.findByStatusInAndLeaseExpiresAtLessThanEqual(
				eq(List.of("QUEUED", "RUNNING")), any(LocalDateTime.class)))
				.thenReturn(List.of(queued, running));
		Executor executor = mock(Executor.class);
		var service = new ScheduleGenerationJobService(jobs, mock(AutoSchedulingService.class),
				mock(IAuditLogService.class), new TransactionTemplate(manager), executor);

		service.recoverInterruptedJobs();

		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(jobs).failExpired(eq("expired"), message.capture(), any(LocalDateTime.class));
		ArgumentCaptor<String> committedMessage = ArgumentCaptor.forClass(String.class);
		verify(jobs).failExpired(eq("raced"), committedMessage.capture(), any(LocalDateTime.class));
		assertThat(message.getValue()).contains("新建 AI Run", "核实已生成的候选", "可能已提交");
		assertThat(committedMessage.getValue()).contains("已有候选方案提交记录", "未确认成功");
		assertThat(running.getStatus()).isEqualTo("RUNNING");
		verify(jobs, never()).save(any());
		verifyNoInteractions(executor);
	}

	@Test
	void queuedJobCannotRunUnlessItsAtomicClaimSucceeds() {
		var fixture = fixture();
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		verify(fixture.jobs()).claim(eq("job-1"), any(), any(), any());
		verifyNoInteractions(fixture.scheduling());
		verify(fixture.jobs(), never()).succeed(any(), any(), any(), any());
	}

	@Test
	void activeOwnerRenewsAndWritesProgressThenMarksCommittedCandidatesSucceeded() {
		var fixture = fixture();
		when(fixture.jobs().claim(any(), any(), any(), any())).thenReturn(1);
		when(fixture.jobs().heartbeat(any(), any(), any(), any())).thenReturn(1);
		when(fixture.jobs().progress(any(), any(), anyInt(), any(), any())).thenReturn(1);
		when(fixture.jobs().recordCommittedCandidates(any(), any(), any(), any(), any()))
				.thenReturn(1);
		when(fixture.jobs().succeed(any(), any(), any(), any())).thenReturn(1);
		var candidate = mock(ScheduleCandidateView.class);
		when(candidate.id()).thenReturn("candidate-1");
		when(fixture.scheduling().generate(any(), any(), any(BooleanSupplier.class),
				any(IntConsumer.class), any())).thenAnswer(invocation -> {
					fixture.service().heartbeatWorkers();
					IntConsumer progress = invocation.getArgument(3);
					progress.accept(50);
					return List.of(candidate);
				});
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
		verify(fixture.jobs()).claim(eq("job-1"), owner.capture(), any(), any());
		verify(fixture.jobs()).heartbeat(eq("job-1"), eq(owner.getValue()), any(), any());
		verify(fixture.jobs()).progress(eq("job-1"), eq(owner.getValue()), eq(50), any(), any());
		verify(fixture.jobs()).recordCommittedCandidates(eq("job-1"), eq(owner.getValue()),
				eq("[\"candidate-1\"]"), any(), any());
		verify(fixture.jobs()).succeed(eq("job-1"), eq(owner.getValue()),
				eq("[\"candidate-1\"]"), any());
		assertThat(owner.getValue()).isNotBlank();
	}

	@Test
	void lostLeaseDuringProgressStopsGenerationAndCannotOverwriteAnotherOwner() {
		var fixture = fixture();
		when(fixture.jobs().claim(any(), any(), any(), any())).thenReturn(1);
		when(fixture.scheduling().generate(any(), any(), any(BooleanSupplier.class),
				any(IntConsumer.class), any())).thenAnswer(invocation -> {
					IntConsumer progress = invocation.getArgument(3);
					assertThatThrownBy(() -> progress.accept(50))
							.isInstanceOf(IllegalStateException.class).hasMessageContaining("租约失效");
					BooleanSupplier cancelled = invocation.getArgument(2);
					assertThat(cancelled.getAsBoolean()).isTrue();
					throw new IllegalStateException("rollback");
				});
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		verify(fixture.jobs(), never()).failOwned(any(), any(), any(), any());
		verify(fixture.jobs(), never()).succeed(any(), any(), any(), any());
	}

	@Test
	void committedCandidateIdsDoNotAllowLostOwnerToRecordSuccessOrFailure() {
		var fixture = fixture();
		when(fixture.jobs().claim(any(), any(), any(), any())).thenReturn(1);
		when(fixture.jobs().recordCommittedCandidates(any(), any(), any(), any(), any()))
				.thenReturn(1);
		when(fixture.scheduling().generate(any(), any(), any(BooleanSupplier.class),
				any(IntConsumer.class), any())).thenReturn(List.of());
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		verify(fixture.jobs()).succeed(eq("job-1"), any(), eq("[]"), any());
		verify(fixture.jobs(), never()).failOwned(any(), any(), any(), any());
	}

	@Test
	void committedCandidatesCannotBeRecordedAfterLeaseLoss() {
		var fixture = fixture();
		when(fixture.jobs().claim(any(), any(), any(), any())).thenReturn(1);
		when(fixture.scheduling().generate(any(), any(), any(BooleanSupplier.class),
				any(IntConsumer.class), any())).thenReturn(List.of());
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		verify(fixture.jobs()).recordCommittedCandidates(eq("job-1"), any(), eq("[]"),
				any(), any());
		verify(fixture.jobs(), never()).succeed(any(), any(), any(), any());
		verify(fixture.jobs(), never()).failOwned(any(), any(), any(), any());
	}

	@Test
	void runningFailureOnlyFailsTheCurrentLeaseOwner() {
		var fixture = fixture();
		when(fixture.jobs().claim(any(), any(), any(), any())).thenReturn(1);
		when(fixture.scheduling().generate(any(), any(), any(BooleanSupplier.class),
				any(IntConsumer.class), any())).thenThrow(new IllegalArgumentException("bad input"));
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
		verify(fixture.jobs()).claim(eq("job-1"), owner.capture(), any(), any());
		verify(fixture.jobs()).failOwned(eq("job-1"), eq(owner.getValue()),
				eq("bad input"), any());
	}

	@Test
	void heartbeatDetectsRemoteCancellationBeforeSolverFinishes() {
		var fixture = fixture();
		when(fixture.jobs().claim(any(), any(), any(), any())).thenReturn(1);
		when(fixture.scheduling().generate(any(), any(), any(BooleanSupplier.class),
				any(IntConsumer.class), any())).thenAnswer(invocation -> {
					fixture.service().heartbeatWorkers();
					BooleanSupplier cancelled = invocation.getArgument(2);
					assertThat(cancelled.getAsBoolean()).isTrue();
					throw new IllegalStateException("cancelled");
				});
		fixture.service().submit(command(), "admin");
		fixture.dispatched().get().run();
		verify(fixture.jobs(), never()).succeed(any(), any(), any(), any());
		verify(fixture.jobs(), never()).failOwned(any(), any(), any(), any());
	}

	@Test
	void cancellationAndDispatchFailureUseConditionalTransitions() {
		var fixture = fixture();
		when(fixture.jobs().cancelActive(eq("job-1"), any(), any())).thenReturn(1, 0);
		var saved = fixture.service().submit(command(), "admin");
		when(fixture.jobs().findById("job-1")).thenReturn(Optional.of(saved));
		fixture.service().cancel("job-1", "admin");
		assertThatThrownBy(() -> fixture.service().cancel("job-1", "admin"))
				.isInstanceOf(IllegalStateException.class);
		verify(fixture.jobs(), never()).saveAndFlush(any());

		ScheduleGenerationJobRepository jobs = mockJobs();
		when(jobs.save(any())).thenAnswer(invocation -> {
			ScheduleGenerationJob job = invocation.getArgument(0);
			job.setId("job-2");
			return job;
		});
		Executor rejecting = runnable -> { throw new IllegalStateException("rejected"); };
		assertThatThrownBy(() -> service(jobs, mock(AutoSchedulingService.class), rejecting)
				.submit(command(), "admin")).isInstanceOf(IllegalStateException.class);
		verify(jobs).failQueued(eq("job-2"), eq("rejected"), any());
		verify(jobs, never()).failOwned(any(), any(), any(), any());
	}

	private static AutoScheduleCommand command() {
		return new AutoScheduleCommand("2026-2027-1", "AI", "FULL", Set.of(),
				1, 5, 8, 1, 20);
	}

	private static ScheduleGenerationJobRepository mockJobs() {
		ScheduleGenerationJobRepository jobs = mock(ScheduleGenerationJobRepository.class);
		when(jobs.databaseTime()).thenAnswer(invocation -> LocalDateTime.now());
		return jobs;
	}

	private static ScheduleGenerationJobService service(ScheduleGenerationJobRepository jobs,
			AutoSchedulingService scheduling, Executor executor) {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		return new ScheduleGenerationJobService(jobs, scheduling, mock(IAuditLogService.class),
				new TransactionTemplate(manager), executor);
	}

	private static Fixture fixture() {
		ScheduleGenerationJobRepository jobs = mockJobs();
		when(jobs.save(any())).thenAnswer(invocation -> {
			ScheduleGenerationJob job = invocation.getArgument(0);
			job.setId("job-1");
			return job;
		});
		AtomicReference<Runnable> dispatched = new AtomicReference<>();
		AutoSchedulingService scheduling = mock(AutoSchedulingService.class);
		return new Fixture(service(jobs, scheduling, dispatched::set), jobs, scheduling, dispatched);
	}

	private record Fixture(ScheduleGenerationJobService service,
			ScheduleGenerationJobRepository jobs, AutoSchedulingService scheduling,
			AtomicReference<Runnable> dispatched) {
	}

	@Test
	void nestedAiSubmissionWaitsForRunTransactionCommit() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		TransactionTemplate transactions = new TransactionTemplate(manager);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mockJobs();
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
		ScheduleGenerationJobRepository jobs = mockJobs();
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
		assertThat(first.getLeaseExpiresAt()).isAfter(LocalDateTime.now());
		assertThat(first.getLeaseOwner()).isNull();
		verify(jobs).save(any());
		verify(executor).execute(any());
		assertThatThrownBy(() -> service.submit(command, "different", "run-1", constraints))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void uniqueRunRaceReturnsCommittedJobWithoutDispatchingAnother() {
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		ScheduleGenerationJobRepository jobs = mockJobs();
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
