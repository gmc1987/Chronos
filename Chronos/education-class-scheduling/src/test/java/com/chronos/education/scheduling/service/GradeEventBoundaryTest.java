package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.grade.dto.GradeSourceEventContracts.ExamScoresConfirmedV1;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.model.DataGradeEventFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class GradeEventBoundaryTest {
	@Test
	void rejectsExamProjectionWithoutOfferingAndLeavesNoFact() throws Exception {
		DataGradeEventFactRepository facts = mock(DataGradeEventFactRepository.class);
		EducationGradeEventConsumer consumer = new EducationGradeEventConsumer(
				facts, new ObjectMapper().findAndRegisterModules());
		ExamScoresConfirmedV1 event = new ExamScoresConfirmedV1(
				"exam-1", "ExamScoresConfirmedV1", OffsetDateTime.now(), 1,
				"plan-1", "session-1", null, "student-1",
				BigDecimal.TEN, BigDecimal.TEN, null, OffsetDateTime.now());

		assertThatThrownBy(() -> consumer.consume(event.eventType(),
				new ObjectMapper().findAndRegisterModules().writeValueAsString(event)))
				.isInstanceOf(GradeEventUnavailableException.class)
				.satisfies(error -> org.junit.jupiter.api.Assertions.assertEquals(
						"GRADE_EVENT_UNAVAILABLE_OFFERING",
						((GradeEventUnavailableException) error).code()));
		verify(facts, never()).save(any(DataGradeEventFact.class));
	}
}
