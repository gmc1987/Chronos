package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.grade.dto.GradeSourceEventContracts.CourseGradesPublishedV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainEventOutboxService {
	private final DomainEventOutboxRepository outbox;
	private final ObjectMapper json;

	public DomainEventOutboxService(DomainEventOutboxRepository outbox, ObjectMapper json) {
		this.outbox = outbox;
		this.json = json;
	}

	@Transactional
	public void enqueue(CourseGradesPublishedV1 event) {
		if (outbox.existsByDeduplicationKey(event.eventId())) {
			return;
		}
		DomainEventOutbox record = new DomainEventOutbox();
		record.setEventType(event.eventType());
		record.setAggregateId(event.gradebookId());
		record.setPayloadJson(write(event));
		record.setDeduplicationKey(event.eventId());
		record.setNextAttemptAt(LocalDateTime.now());
		outbox.save(record);
	}

	@Transactional
	public List<DomainEventOutbox> claimBatch(int batchSize, LocalDateTime now, long leaseSeconds) {
		List<DomainEventOutbox> claimed = outbox.findDispatchCandidates(now, PageRequest.of(0, batchSize));
		LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);
		for (DomainEventOutbox event : claimed) {
			event.setStatus("PROCESSING");
			event.setLeaseUntil(leaseUntil);
			outbox.save(event);
		}
		return claimed;
	}

	@Transactional
	public void markSent(String id) {
		DomainEventOutbox event = outbox.findById(id).orElseThrow();
		event.setStatus("SENT");
		event.setSentAt(LocalDateTime.now());
		event.setLeaseUntil(null);
		event.setLastError(null);
	}

	@Transactional
	public void markFailed(String id, Exception failure, int maxAttempts) {
		DomainEventOutbox event = outbox.findById(id).orElseThrow();
		int attempts = event.getAttempts() + 1;
		event.setAttempts(attempts);
		event.setLeaseUntil(null);
		event.setLastError(limit(failure.getMessage(), 1000));
		if (attempts >= maxAttempts) {
			event.setStatus("DEAD");
		} else {
			event.setStatus("PENDING");
			event.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(attempts, 6))));
		}
	}

	private String write(Object event) {
		try {
			return json.writeValueAsString(event);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("领域事件序列化失败", exception);
		}
	}

	private String limit(String message, int maxLength) {
		if (message == null) {
			return "unknown delivery failure";
		}
		return message.length() <= maxLength ? message : message.substring(0, maxLength);
	}
}
