package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.EducationDomainOutboxRepository;
import com.chronos.education.scheduling.model.EducationDomainOutbox;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.WrongAnswerConfirmed;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EducationDomainEventServiceTest {
	@Test
	void dispatchMarksSuccessfullyConsumedEventAsProcessed() throws Exception {
		EducationDomainOutboxRepository outbox = mock(EducationDomainOutboxRepository.class);
		ResearchErrorService errors = mock(ResearchErrorService.class);
		ObjectMapper json = new ObjectMapper().findAndRegisterModules();
		EducationDomainEventService service = new EducationDomainEventService(outbox, errors, json);
		WrongAnswerConfirmed payload = new WrongAnswerConfirmed(
				"event-1",
				"student-1",
				"course-1",
				"term-1",
				null,
				"source-1",
				"exam-1",
				"第一题",
				"EXAM",
				null,
				LocalDateTime.now());
		EducationDomainOutbox event = new EducationDomainOutbox();
		event.setEventId(payload.eventId());
		event.setEventType("WrongAnswerConfirmed");
		event.setActor("admin");
		event.setPayloadJson(json.writeValueAsString(payload));
		event.setNextAttemptAt(LocalDateTime.now());
		when(outbox.lockDispatchBatch(any())).thenReturn(List.of(event));

		service.dispatch();

		assertThat(event.getStatus()).isEqualTo("PROCESSED");
		assertThat(event.getProcessedAt()).isNotNull();
		verify(errors).onTrustedWrongAnswerConfirmed(any(), any());
		verify(outbox).save(event);
	}

	@Test
	void dispatchRetainsFailureForRetry() throws Exception {
		EducationDomainOutboxRepository outbox = mock(EducationDomainOutboxRepository.class);
		ResearchErrorService errors = mock(ResearchErrorService.class);
		ObjectMapper json = new ObjectMapper().findAndRegisterModules();
		EducationDomainEventService service = new EducationDomainEventService(outbox, errors, json);
		EducationDomainOutbox event = new EducationDomainOutbox();
		event.setEventType("UnsupportedEvent");
		event.setActor("admin");
		event.setPayloadJson("{}");
		event.setNextAttemptAt(LocalDateTime.now());
		when(outbox.lockDispatchBatch(any())).thenReturn(List.of(event));

		service.dispatch();

		assertThat(event.getStatus()).isEqualTo("PENDING");
		assertThat(event.getAttempts()).isEqualTo(1);
		assertThat(event.getLastError()).contains("不支持的教育领域事件");
		assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now());
	}
}
