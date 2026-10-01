package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;
import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.scheduling.dao.DataEventConsumptionRepository;
import com.chronos.education.scheduling.model.DataEventConsumption;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EducationDomainEventConsumerTest {
	@Test
	void consumesPublishedHomeNoticeExactlyOnce() {
		DomainEventOutboxRepository outbox = mock(DomainEventOutboxRepository.class);
		DataEventConsumptionRepository consumptions = mock(DataEventConsumptionRepository.class);
		DomainEventOutbox source = source(validPayload("event-1"));
		when(consumptions.findByEventId("event-1")).thenReturn(Optional.empty());
		when(consumptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		EducationDomainEventConsumer consumer = new EducationDomainEventConsumer(
				outbox, consumptions, mapper(), 3);

		consumer.consumeOne(source);
		ArgumentCaptor<DataEventConsumption> captured = ArgumentCaptor.forClass(DataEventConsumption.class);
		verify(consumptions).save(captured.capture());
		DataEventConsumption saved = captured.getValue();

		assertThat(saved.getStatus()).isEqualTo("PROCESSED");
		assertThat(saved.getAttempts()).isZero();
	}

	@Test
	void malformedEventRetriesAndEventuallyDeadLetters() {
		DomainEventOutboxRepository outbox = mock(DomainEventOutboxRepository.class);
		DataEventConsumptionRepository consumptions = mock(DataEventConsumptionRepository.class);
		DomainEventOutbox source = source("{}");
		DataEventConsumption existing = new DataEventConsumption();
		existing.setEventId("event-1");
		existing.setStatus("PENDING");
		existing.setAttempts(2);
		existing.setNextAttemptAt(java.time.LocalDateTime.now().minusSeconds(1));
		when(consumptions.findByEventId("event-1")).thenReturn(Optional.of(existing));
		when(consumptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		EducationDomainEventConsumer consumer = new EducationDomainEventConsumer(
				outbox, consumptions, mapper(), 3);

		consumer.consumeOne(source);

		assertThat(existing.getStatus()).isEqualTo("DEAD");
		assertThat(existing.getAttempts()).isEqualTo(3);
		assertThat(existing.getLastError()).isNotBlank();
	}

	@Test
	void processedEventIsNotWrittenAgain() {
		DomainEventOutboxRepository outbox = mock(DomainEventOutboxRepository.class);
		DataEventConsumptionRepository consumptions = mock(DataEventConsumptionRepository.class);
		DataEventConsumption existing = new DataEventConsumption();
		existing.setEventId("event-1");
		existing.setStatus("PROCESSED");
		existing.setNextAttemptAt(java.time.LocalDateTime.now());
		when(consumptions.findByEventId("event-1")).thenReturn(Optional.of(existing));
		EducationDomainEventConsumer consumer = new EducationDomainEventConsumer(
				outbox, consumptions, mapper(), 3);

		consumer.consumeOne(source(validPayload("event-1")));

		verify(consumptions, never()).save(any());
	}

	private DomainEventOutbox source(String payload) {
		DomainEventOutbox source = new DomainEventOutbox();
		source.setEventType("HomeNoticePublishedV1");
		source.setAggregateId("notice-1");
		source.setDeduplicationKey("event-1");
		source.setPayloadJson(payload);
		return source;
	}

	private String validPayload(String eventId) {
		return "{\"eventId\":\"" + eventId
				+ "\",\"eventType\":\"HomeNoticePublishedV1\",\"occurredAt\":\"2026-09-19T12:00:00+08:00\","
				+ "\"payloadVersion\":1,\"noticeId\":\"notice-1\",\"classId\":\"class-1\","
				+ "\"targetCount\":2,\"publisherUsername\":\"teacher\"}";
	}

	private ObjectMapper mapper() {
		return new ObjectMapper().registerModule(new JavaTimeModule());
	}
}
