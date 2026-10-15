package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.grade.dao.GradeItemRepository;
import com.chronos.education.grade.model.AssessmentComponent;
import com.chronos.education.grade.model.GradeItem;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.model.GradebookStudent;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamSessionOfferingRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.DataGradeEventFact;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.ExamSessionOffering;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExamGradebookSourceServiceTest {
	@Mock CourseOfferingRepository offerings;
	@Mock ExamPlanRepository plans;
	@Mock ExamSessionRepository sessions;
	@Mock ExamSessionOfferingRepository mappings;
	@Mock DataGradeEventFactRepository facts;
	@Mock GradeItemRepository items;
	@InjectMocks ExamGradebookSourceService service;

	@Test
	void importsPublishedFactWithSourceProvenanceAndNormalizedScore() {
		arrangePublishedSource();
		DataGradeEventFact fact = new DataGradeEventFact();
		fact.setEventId("event-1");
		fact.setStudentId("student-1");
		fact.setScore(new BigDecimal("8"));
		fact.setMaxScore(new BigDecimal("10"));
		when(facts.findByEventTypeAndAggregateIdAndOfferingId(
				"ExamScoresConfirmedV1", "session-1", "offering-1"))
				.thenReturn(List.of(fact));
		when(items.findByGradebookId("book-1")).thenReturn(List.of());
		GradebookStudent student = student();

		int imported = service.sync(gradebook(), List.of(component()), List.of(student));

		assertEquals(1, imported);
		ArgumentCaptor<GradeItem> capture = ArgumentCaptor.forClass(GradeItem.class);
		verify(items).save(capture.capture());
		assertEquals(new BigDecimal("16.00"), capture.getValue().getConvertedScore());
		assertEquals("event-1", capture.getValue().getSourceEventId());
	}

	@Test
	void missingStudentFactRejectsWholeImport() {
		arrangePublishedSource();
		when(items.findByGradebookId("book-1")).thenReturn(List.of());
		when(facts.findByEventTypeAndAggregateIdAndOfferingId(
				"ExamScoresConfirmedV1", "session-1", "offering-1"))
				.thenReturn(List.of());

		assertThrows(IllegalStateException.class,
				() -> service.sync(gradebook(), List.of(component()), List.of(student())));
		verify(items, never()).save(any());
	}

	private void arrangePublishedSource() {
		CourseOffering offering = new CourseOffering();
		offering.setSemesterCode("2026-2027-1");
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		ExamPlan plan = new ExamPlan();
		plan.setSemesterCode("2026-2027-1");
		plan.setStatus("PUBLISHED");
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
		ExamSession session = new ExamSession();
		session.setId("session-1");
		session.setPlanId("plan-1");
		session.setStatus("PUBLISHED");
		session.setScoreStatus("PUBLISHED");
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		ExamSessionOffering mapping = new ExamSessionOffering();
		mapping.setOfferingId("offering-1");
		when(mappings.findBySessionId("session-1")).thenReturn(List.of(mapping));
	}

	private Gradebook gradebook() {
		Gradebook value = new Gradebook();
		value.setId("book-1");
		value.setOfferingId("offering-1");
		return value;
	}

	private AssessmentComponent component() {
		AssessmentComponent value = new AssessmentComponent();
		value.setId("component-1");
		value.setSourceType("EXAM");
		value.setSourceRefId("session-1");
		value.setMaxScore(new BigDecimal("20"));
		return value;
	}

	private GradebookStudent student() {
		GradebookStudent value = new GradebookStudent();
		value.setStudentId("student-1");
		value.setStudentNo("S-001");
		return value;
	}
}
