package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.scheduling.dao.DataEventConsumptionRepository;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.dao.EducationDomainOutboxRepository;
import com.chronos.education.scheduling.model.EducationDomainOutbox;
import java.util.List;
import org.junit.jupiter.api.Test;

class EducationDataEventOperationsServiceTest {
	@Test
	void reconciliationComparesEachSupportedSourceWithItsProjection() {
		EducationDomainOutboxRepository gradeOutbox = mock(EducationDomainOutboxRepository.class);
		DataGradeEventFactRepository gradeFacts = mock(DataGradeEventFactRepository.class);
		DomainEventOutboxRepository publishedOutbox = mock(DomainEventOutboxRepository.class);
		DataEventConsumptionRepository consumptions = mock(DataEventConsumptionRepository.class);

		EducationDomainOutbox exam = gradeEvent("ExamScoresConfirmedV1", "PROCESSED");
		EducationDomainOutbox homework = gradeEvent("HomeworkGradesPublishedV1", "DEAD");
		when(gradeOutbox.findByEventTypeIn(org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of(exam, homework));
		when(gradeFacts.countByEventType("ExamScoresConfirmedV1")).thenReturn(1L);
		when(gradeFacts.countByEventType("HomeworkGradesPublishedV1")).thenReturn(0L);
		when(publishedOutbox.findByEventTypeInOrderByCreateTimeAsc(
				List.of("HomeNoticePublishedV1")))
				.thenReturn(List.of(new DomainEventOutbox()));
		when(consumptions.countByEventTypeAndStatus("HomeNoticePublishedV1", "PROCESSED"))
				.thenReturn(1L);

		EducationDataEventOperationsService service = new EducationDataEventOperationsService(
				gradeOutbox,
				gradeFacts,
				publishedOutbox,
				consumptions,
				mock(EducationDomainEventService.class),
				mock(EducationDomainEventConsumptionProcessor.class));

		var result = service.reconciliation();

		assertThat(result).filteredOn(row -> "ExamScoresConfirmedV1".equals(row.eventType()))
				.singleElement()
				.satisfies(row -> assertThat(row.consistent()).isTrue());
		assertThat(result).filteredOn(row -> "HomeworkGradesPublishedV1".equals(row.eventType()))
				.singleElement()
				.satisfies(row -> {
					assertThat(row.deadCount()).isEqualTo(1);
					assertThat(row.consistent()).isFalse();
				});
		assertThat(result).filteredOn(row -> "HomeNoticePublishedV1".equals(row.eventType()))
				.singleElement()
				.satisfies(row -> assertThat(row.consistent()).isTrue());
	}

	@Test
	void retryRoutesToTheCorrectDurableConsumer() {
		EducationDomainEventService gradeEvents = mock(EducationDomainEventService.class);
		EducationDomainEventConsumptionProcessor publishedEvents =
				mock(EducationDomainEventConsumptionProcessor.class);
		EducationDataEventOperationsService service = new EducationDataEventOperationsService(
				mock(EducationDomainOutboxRepository.class),
				mock(DataGradeEventFactRepository.class),
				mock(DomainEventOutboxRepository.class),
				mock(DataEventConsumptionRepository.class),
				gradeEvents,
				publishedEvents);

		service.retry("GRADE", "grade-id");
		service.retry("PUBLISHED", "published-id");

		verify(gradeEvents).retry("grade-id");
		verify(publishedEvents).retry("published-id");
	}

	private EducationDomainOutbox gradeEvent(String type, String status) {
		EducationDomainOutbox event = new EducationDomainOutbox();
		event.setEventType(type);
		event.setStatus(status);
		return event;
	}
}
