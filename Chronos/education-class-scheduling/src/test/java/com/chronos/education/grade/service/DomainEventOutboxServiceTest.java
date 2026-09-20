package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.dto.GradeSourceEventContracts.CourseGradesPublishedV1;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class DomainEventOutboxServiceTest {
	@Test
	void enqueueIsIdempotentAndSerializesPublishedGradeEvent() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		when(repository.existsByDeduplicationKey("event-1")).thenReturn(false);
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper().registerModule(new JavaTimeModule()));

		service.enqueue(event("event-1"));

		verify(repository).save(any(DomainEventOutbox.class));
		verify(repository).existsByDeduplicationKey("event-1");
	}

	@Test
	void duplicateEventIsNotWritten() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		when(repository.existsByDeduplicationKey("event-1")).thenReturn(true);
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper().registerModule(new JavaTimeModule()));

		service.enqueue(event("event-1"));

		verify(repository, never()).save(any());
	}

	@Test
	void failedDeliveryUsesDeadStatusAfterMaximumAttempts() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = new DomainEventOutbox();
		record.setId("outbox-1");
		record.setStatus("PROCESSING");
		record.setClaimToken("claim-1");
		record.setAttempts(9);
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper().registerModule(new JavaTimeModule()));

		boolean updated = service.markFailed(
				"outbox-1",
				"claim-1",
				new IllegalStateException("webhook unavailable"),
				10);

		assertThat(updated).isTrue();
		assertThat(record.getStatus()).isEqualTo("DEAD");
		assertThat(record.getAttempts()).isEqualTo(10);
		assertThat(record.getLastError()).isEqualTo("webhook unavailable");
		assertThat(record.getClaimToken()).isNull();
	}

	@Test
	void staleWorkerCannotOverwriteEventAfterLeaseWasReclaimed() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = new DomainEventOutbox();
		record.setId("outbox-1");
		record.setStatus("PROCESSING");
		record.setClaimToken("new-claim");
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		DomainEventOutboxService service = service(repository);

		boolean sent = service.markSent("outbox-1", "expired-claim");
		boolean failed = service.markFailed(
				"outbox-1",
				"expired-claim",
				new IllegalStateException("late failure"),
				10);

		assertThat(sent).isFalse();
		assertThat(failed).isFalse();
		assertThat(record.getStatus()).isEqualTo("PROCESSING");
		assertThat(record.getClaimToken()).isEqualTo("new-claim");
		assertThat(record.getAttempts()).isZero();
	}

	@Test
	void retryResetsDeadEventForImmediateDelivery() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = deadEvent();
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		when(repository.save(record)).thenReturn(record);
		DomainEventOutboxService service = service(repository);

		DomainEventOutbox result = service.retry("outbox-1");

		assertThat(result.getStatus()).isEqualTo("PENDING");
		assertThat(result.getAttempts()).isZero();
		assertThat(result.getNextAttemptAt()).isNotNull();
		assertThat(result.getLeaseUntil()).isNull();
		assertThat(result.getLastError()).isNull();
	}

	@Test
	void ignoreKeepsDeadEventForAuditAndStopsDelivery() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = deadEvent();
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		when(repository.save(record)).thenReturn(record);
		DomainEventOutboxService service = service(repository);

		DomainEventOutbox result = service.ignore("outbox-1");

		assertThat(result.getStatus()).isEqualTo("IGNORED");
		assertThat(result.getLastError()).isEqualTo("webhook unavailable");
		assertThat(result.getLeaseUntil()).isNull();
	}

	@Test
	void activeEventCannotBeRetriedOrIgnoredByAdministrator() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = deadEvent();
		record.setStatus("SENDING");
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		DomainEventOutboxService service = service(repository);

		assertThatThrownBy(() -> service.retry("outbox-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("只有死信事件可以执行该操作");
		assertThatThrownBy(() -> service.ignore("outbox-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("只有死信事件可以执行该操作");
		verify(repository, never()).save(any());
	}

	@Test
	void deadEventQueryUsesBoundedPageSize() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutboxService service = service(repository);

		service.deadEvents(-1, 500);

		verify(repository).findByStatusOrderByCreateTimeDesc(
				"DEAD",
				PageRequest.of(0, 100));
	}

	private DomainEventOutboxService service(DomainEventOutboxRepository repository) {
		return new DomainEventOutboxService(
				repository,
				new ObjectMapper().registerModule(new JavaTimeModule()));
	}

	private DomainEventOutbox deadEvent() {
		DomainEventOutbox record = new DomainEventOutbox();
		record.setId("outbox-1");
		record.setStatus("DEAD");
		record.setAttempts(10);
		record.setLeaseUntil(OffsetDateTime.parse("2026-09-19T12:00:00+08:00").toLocalDateTime());
		record.setLastError("webhook unavailable");
		return record;
	}

	private CourseGradesPublishedV1 event(String id) {
		return new CourseGradesPublishedV1(
				id,
				"CourseGradesPublishedV1",
				OffsetDateTime.parse("2026-09-19T12:00:00+08:00"),
				1,
				"gradebook-1",
				"offering-1",
				2,
				"hash",
				"teacher");
	}
}
