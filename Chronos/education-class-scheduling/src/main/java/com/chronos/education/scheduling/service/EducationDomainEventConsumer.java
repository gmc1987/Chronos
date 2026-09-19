package com.chronos.education.scheduling.service;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.homeschool.dto.HomeNoticeEventContracts.HomeNoticePublishedV1;
import com.chronos.education.scheduling.dao.DataEventConsumptionRepository;
import com.chronos.education.scheduling.model.DataEventConsumption;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes only published, versioned contracts that are already durably stored in the education outbox.
 * Consumption is an auditable side effect and never calls an external service.
 */
@Service
public class EducationDomainEventConsumer {
	private static final String HOME_NOTICE_PUBLISHED = "HomeNoticePublishedV1";

	private final DomainEventOutboxRepository outbox;
	private final DataEventConsumptionRepository consumptions;
	private final ObjectMapper json;
	private final int maxAttempts;

	public EducationDomainEventConsumer(
			DomainEventOutboxRepository outbox,
			DataEventConsumptionRepository consumptions,
			ObjectMapper json,
			@Value("${chronos.education.data-center.events.max-attempts:10}") int maxAttempts) {
		this.outbox = outbox;
		this.consumptions = consumptions;
		this.json = json;
		this.maxAttempts = maxAttempts;
	}

	@Scheduled(fixedDelayString = "${chronos.education.data-center.events.poll-delay-ms:5000}")
	public void consumePublishedEvents() {
		for (DomainEventOutbox event : outbox.findByEventTypeInOrderByCreateTimeAsc(List.of(HOME_NOTICE_PUBLISHED))) {
			consumeOne(event);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void consumeOne(DomainEventOutbox source) {
		LocalDateTime now = LocalDateTime.now();
		DataEventConsumption consumption = consumptions.findByEventId(source.getDeduplicationKey())
				.orElseGet(() -> newConsumption(source, now));
		if ("PROCESSED".equals(consumption.getStatus()) || "DEAD".equals(consumption.getStatus())
				|| consumption.getNextAttemptAt() != null && consumption.getNextAttemptAt().isAfter(now)) {
			return;
		}
		try {
			HomeNoticePublishedV1 event = json.readValue(source.getPayloadJson(), HomeNoticePublishedV1.class);
			if (!HOME_NOTICE_PUBLISHED.equals(event.eventType())
					|| event.payloadVersion() != 1
					|| !source.getDeduplicationKey().equals(event.eventId())) {
				throw new IllegalArgumentException("家校通知事件契约不匹配");
			}
			consumption.setStatus("PROCESSED");
			consumption.setProcessedAt(now);
			consumption.setLastError(null);
			consumption.setNextAttemptAt(now);
		} catch (Exception failure) {
			consumption.setAttempts(consumption.getAttempts() + 1);
			consumption.setStatus(consumption.getAttempts() >= maxAttempts ? "DEAD" : "PENDING");
			consumption.setNextAttemptAt(now.plusMinutes(Math.min(60, 1L << Math.min(consumption.getAttempts(), 6))));
			consumption.setLastError(limit(failure.getMessage(), 1000));
		}
		consumptions.save(consumption);
	}

	private DataEventConsumption newConsumption(DomainEventOutbox source, LocalDateTime now) {
		DataEventConsumption consumption = new DataEventConsumption();
		consumption.setEventId(source.getDeduplicationKey());
		consumption.setEventType(source.getEventType());
		consumption.setAggregateId(source.getAggregateId());
		consumption.setPayloadJson(source.getPayloadJson());
		consumption.setNextAttemptAt(now);
		return consumption;
	}

	private String limit(String message, int maxLength) {
		if (message == null) {
			return "unknown event consumption failure";
		}
		return message.length() <= maxLength ? message : message.substring(0, maxLength);
	}
}
