package com.chronos.education.scheduling.service;

import com.chronos.education.grade.dto.GradeSourceEventContracts.CourseGradesPublishedV1;
import com.chronos.education.grade.dto.GradeSourceEventContracts.ExamScoresConfirmedV1;
import com.chronos.education.grade.dto.GradeSourceEventContracts.HomeworkGradesPublishedV1;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.model.DataGradeEventFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EducationGradeEventConsumer {
	private final DataGradeEventFactRepository facts;
	private final ObjectMapper json;

	public EducationGradeEventConsumer(DataGradeEventFactRepository facts, ObjectMapper json) {
		this.facts = facts;
		this.json = json;
	}

	@Transactional
	public void consume(String eventType, String payload) throws Exception {
		switch (eventType) {
		case "CourseGradesPublishedV1" -> consumeCourse(json.readValue(payload, CourseGradesPublishedV1.class));
		case "ExamScoresConfirmedV1" -> consumeExam(json.readValue(payload, ExamScoresConfirmedV1.class));
		case "HomeworkGradesPublishedV1" -> consumeHomework(json.readValue(payload, HomeworkGradesPublishedV1.class));
		default -> throw new IllegalArgumentException("不支持的成绩事件：" + eventType);
		}
	}

	private void consumeCourse(CourseGradesPublishedV1 event) {
		save(event.eventId(), event.eventType(), event.gradebookId(), event.occurredAt().toLocalDateTime(),
				event.offeringId(), null, null, null);
	}

	private void consumeExam(ExamScoresConfirmedV1 event) {
		save(event.eventId(), event.eventType(), event.sessionId(), event.occurredAt().toLocalDateTime(),
				event.offeringId(), event.studentId(), event.rawScore(), event.maxScore());
	}

	private void consumeHomework(HomeworkGradesPublishedV1 event) {
		save(event.eventId(), event.eventType(), event.assignmentId(), event.publishedAt().toLocalDateTime(),
				event.offeringId(), event.studentId(), event.score(), event.maxScore());
	}

	private void save(String eventId, String eventType, String aggregateId, LocalDateTime occurredAt,
			String offeringId, String studentId, java.math.BigDecimal score,
			java.math.BigDecimal maxScore) {
		if (facts.existsByEventId(eventId)) {
			return;
		}
		DataGradeEventFact fact = new DataGradeEventFact();
		fact.setEventId(eventId);
		fact.setEventType(eventType);
		fact.setAggregateId(aggregateId);
		fact.setOccurredAt(occurredAt);
		fact.setOfferingId(offeringId);
		fact.setStudentId(studentId);
		fact.setScore(score);
		fact.setMaxScore(maxScore);
		fact.setSourceVersion("v1");
		facts.save(fact);
	}
}
