package com.chronos.education.scheduling.service;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.scheduling.dao.DataEventConsumptionRepository;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.dao.EducationDomainOutboxRepository;
import com.chronos.education.scheduling.model.DataEventConsumption;
import com.chronos.education.scheduling.model.EducationDomainOutbox;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and recovery boundary for data-center event projections. */
@Service
public class EducationDataEventOperationsService {
	private static final List<String> GRADE_EVENT_TYPES = List.of(
			"CourseGradesPublishedV1",
			"ExamScoresConfirmedV1",
			"HomeworkGradesPublishedV1");
	private static final String HOME_NOTICE_EVENT_TYPE = "HomeNoticePublishedV1";

	private final EducationDomainOutboxRepository gradeOutbox;
	private final DataGradeEventFactRepository gradeFacts;
	private final DomainEventOutboxRepository publishedOutbox;
	private final DataEventConsumptionRepository consumptions;
	private final EducationDomainEventService gradeEvents;
	private final EducationDomainEventConsumptionProcessor publishedEvents;

	public EducationDataEventOperationsService(
			EducationDomainOutboxRepository gradeOutbox,
			DataGradeEventFactRepository gradeFacts,
			DomainEventOutboxRepository publishedOutbox,
			DataEventConsumptionRepository consumptions,
			EducationDomainEventService gradeEvents,
			EducationDomainEventConsumptionProcessor publishedEvents) {
		this.gradeOutbox = gradeOutbox;
		this.gradeFacts = gradeFacts;
		this.publishedOutbox = publishedOutbox;
		this.consumptions = consumptions;
		this.gradeEvents = gradeEvents;
		this.publishedEvents = publishedEvents;
	}

	@Transactional(readOnly = true)
	public List<EventReconciliation> reconciliation() {
		List<EducationDomainOutbox> gradeSources = gradeOutbox.findByEventTypeIn(GRADE_EVENT_TYPES);
		List<EventReconciliation> result = new ArrayList<>();
		for (String eventType : GRADE_EVENT_TYPES) {
			long sourceCount = gradeSources.stream()
					.filter(event -> eventType.equals(event.getEventType()))
					.count();
			long pendingCount = gradeSources.stream()
					.filter(event -> eventType.equals(event.getEventType()))
					.filter(event -> "PENDING".equals(event.getStatus()))
					.count();
			long deadCount = gradeSources.stream()
					.filter(event -> eventType.equals(event.getEventType()))
					.filter(event -> "DEAD".equals(event.getStatus()))
					.count();
			long projectedCount = gradeFacts.countByEventType(eventType);
			result.add(new EventReconciliation(
					"GRADE",
					eventType,
					sourceCount,
					projectedCount,
					pendingCount,
					deadCount,
					sourceCount == projectedCount && pendingCount == 0 && deadCount == 0));
		}

		long publishedCount = publishedOutbox
				.findByEventTypeInOrderByCreateTimeAsc(List.of(HOME_NOTICE_EVENT_TYPE))
				.size();
		long processedCount = consumptions.countByEventTypeAndStatus(
				HOME_NOTICE_EVENT_TYPE,
				"PROCESSED");
		long pendingCount = consumptions.countByEventTypeAndStatus(
				HOME_NOTICE_EVENT_TYPE,
				"PENDING");
		long deadCount = consumptions.countByEventTypeAndStatus(
				HOME_NOTICE_EVENT_TYPE,
				"DEAD");
		result.add(new EventReconciliation(
				"PUBLISHED",
				HOME_NOTICE_EVENT_TYPE,
				publishedCount,
				processedCount,
				pendingCount,
				deadCount,
				publishedCount == processedCount && pendingCount == 0 && deadCount == 0));
		return result;
	}

	@Transactional(readOnly = true)
	public List<EventFailure> failures() {
		List<EventFailure> result = new ArrayList<>();
		for (EducationDomainOutbox event : gradeOutbox
				.findByStatusOrderByCreateTimeDesc(
						"DEAD",
						org.springframework.data.domain.PageRequest.of(0, 100))
				.getContent()) {
			result.add(new EventFailure(
					"GRADE",
					event.getId(),
					event.getEventId(),
					event.getEventType(),
					event.getStatus(),
					event.getAttempts(),
					event.getLastError(),
					event.getCreateTime()));
		}
		for (DataEventConsumption event : consumptions
				.findTop100ByLastErrorIsNotNullOrderByCreateTimeDesc()) {
			result.add(new EventFailure(
					"PUBLISHED",
					event.getId(),
					event.getEventId(),
					event.getEventType(),
					event.getStatus(),
					event.getAttempts(),
					event.getLastError(),
					event.getCreateTime()));
		}
		return result.stream()
				.sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
				.limit(100)
				.toList();
	}

	@Transactional
	public void retry(String channel, String id) {
		if ("GRADE".equals(channel)) {
			gradeEvents.retry(id);
			return;
		}
		if ("PUBLISHED".equals(channel)) {
			publishedEvents.retry(id);
			return;
		}
		throw new IllegalArgumentException("未知事件通道");
	}

	public record EventReconciliation(
			String channel,
			String eventType,
			long sourceCount,
			long projectedCount,
			long pendingCount,
			long deadCount,
			boolean consistent) {}

	public record EventFailure(
			String channel,
			String id,
			String eventId,
			String eventType,
			String status,
			int attempts,
			String lastError,
			LocalDateTime createdAt) {}
}
