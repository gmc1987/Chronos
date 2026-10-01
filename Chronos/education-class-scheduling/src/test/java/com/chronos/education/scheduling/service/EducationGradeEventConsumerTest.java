package com.chronos.education.scheduling.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.grade.dto.GradeSourceEventContracts.HomeworkGradesPublishedV1;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.model.DataGradeEventFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EducationGradeEventConsumerTest {
	@Test
	void consumesHomeworkEventIdempotently() throws Exception {
		DataGradeEventFactRepository facts = mock(DataGradeEventFactRepository.class);
		ObjectMapper json = new ObjectMapper().findAndRegisterModules();
		EducationGradeEventConsumer consumer = new EducationGradeEventConsumer(facts, json);
		HomeworkGradesPublishedV1 event = new HomeworkGradesPublishedV1(
				"event-1", "HomeworkGradesPublishedV1", OffsetDateTime.now(), 1,
				"assignment-1", "offering-1", "student-1", BigDecimal.valueOf(80),
				BigDecimal.valueOf(100), OffsetDateTime.now());
		when(facts.existsByEventId("event-1")).thenReturn(false, true);

		consumer.consume(event.eventType(), json.writeValueAsString(event));
		consumer.consume(event.eventType(), json.writeValueAsString(event));

		verify(facts, times(2)).existsByEventId("event-1");
		verify(facts, times(1)).save(any(DataGradeEventFact.class));
	}
}
