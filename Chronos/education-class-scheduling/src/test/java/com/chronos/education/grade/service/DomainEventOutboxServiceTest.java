package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;

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
		record.setAttempts(9);
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper().registerModule(new JavaTimeModule()));

		service.markFailed("outbox-1", new IllegalStateException("webhook unavailable"), 10);

		assertThat(record.getStatus()).isEqualTo("DEAD");
		assertThat(record.getAttempts()).isEqualTo(10);
		assertThat(record.getLastError()).isEqualTo("webhook unavailable");
	}

	@Test
	void replayMakesDeadEventImmediatelyPendingWithoutChangingIdentity() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = new DomainEventOutbox();
		record.setId("outbox-1");
		record.setStatus("DEAD");
		record.setDeduplicationKey("event-1");
		record.setAttempts(10);
		record.setLastError("failed");
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper());

		service.replay("outbox-1");

		assertThat(record.getStatus()).isEqualTo("PENDING");
		assertThat(record.getDeduplicationKey()).isEqualTo("event-1");
		assertThat(record.getLeaseUntil()).isNull();
		assertThat(record.getLastError()).isNull();
	}

	@Test
	void operatorCanMarkAnyEventDeadWithBoundedReason() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = new DomainEventOutbox();
		record.setId("outbox-1");
		when(repository.findById("outbox-1")).thenReturn(Optional.of(record));
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper());

		service.markDead("outbox-1", "x".repeat(1200));

		assertThat(record.getStatus()).isEqualTo("DEAD");
		assertThat(record.getLastError()).hasSize(1000);
		assertThat(record.getLeaseUntil()).isNull();
	}

	@Test
	void expiredProcessingLeaseIsClaimedAgainWithFreshLease() {
		DomainEventOutboxRepository repository = mock(DomainEventOutboxRepository.class);
		DomainEventOutbox record = new DomainEventOutbox();
		record.setId("outbox-1");
		record.setStatus("PROCESSING");
		when(repository.findDispatchCandidates(any(LocalDateTime.class), any(PageRequest.class)))
				.thenReturn(List.of(record));
		DomainEventOutboxService service = new DomainEventOutboxService(repository, new ObjectMapper());

		List<DomainEventOutbox> claimed = service.claimBatch(10, LocalDateTime.now(), 120);

		assertThat(claimed).containsExactly(record);
		assertThat(record.getStatus()).isEqualTo("PROCESSING");
		assertThat(record.getLeaseUntil()).isAfter(LocalDateTime.now());
		verify(repository).save(record);
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
