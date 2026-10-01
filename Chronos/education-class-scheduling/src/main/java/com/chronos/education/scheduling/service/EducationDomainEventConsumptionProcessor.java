package com.chronos.education.scheduling.service;

import com.chronos.education.grade.model.DomainEventOutbox;
import com.chronos.education.homeschool.dto.HomeNoticeEventContracts.HomeNoticePublishedV1;
import com.chronos.education.scheduling.dao.DataEventConsumptionRepository;
import com.chronos.education.scheduling.model.DataEventConsumption;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Processes one event in a transaction proxied by Spring, with a row lock for retries. */
@Service
public class EducationDomainEventConsumptionProcessor {
	private static final String HOME_NOTICE_PUBLISHED = "HomeNoticePublishedV1";

	private final DataEventConsumptionRepository consumptions;
	private final ObjectMapper json;
	private final int maxAttempts;

	public EducationDomainEventConsumptionProcessor(
			DataEventConsumptionRepository consumptions, ObjectMapper json,
			@Value("${chronos.education.data-center.events.max-attempts:10}") int maxAttempts) {
		this.consumptions = consumptions;
		this.json = json;
		this.maxAttempts = maxAttempts;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void process(DomainEventOutbox source) {
		LocalDateTime now = LocalDateTime.now();
		consumptions.claimIfAbsent(source.getDeduplicationKey(), source.getEventType(),
				source.getAggregateId(), source.getPayloadJson(), now);
		DataEventConsumption consumption = consumptions.findByEventIdForUpdate(source.getDeduplicationKey())
				.orElseThrow(() -> new IllegalStateException("事件消费记录未创建"));
		try {
			if (isFinishedOrNotDue(consumption, now)) {
				return;
			}
			validate(source);
			consumption.setStatus("PROCESSED");
			consumption.setProcessedAt(now);
			consumption.setLastError(null);
			consumption.setNextAttemptAt(now);
		} catch (Exception failure) {
			consumption = consumptions.findByEventIdForUpdate(source.getDeduplicationKey())
					.orElseThrow(() -> new IllegalStateException("事件消费记录不存在"));
			consumption.setAttempts(consumption.getAttempts() + 1);
			consumption.setStatus(consumption.getAttempts() >= maxAttempts ? "DEAD" : "PENDING");
			consumption.setNextAttemptAt(
					now.plusMinutes(Math.min(60, 1L << Math.min(consumption.getAttempts(), 6))));
			consumption.setLastError(limit(failure.getMessage(), 1000));
		}
		consumptions.save(consumption);
	}

	private void validate(DomainEventOutbox source) throws Exception {
		HomeNoticePublishedV1 event = json.readValue(source.getPayloadJson(), HomeNoticePublishedV1.class);
		if (!HOME_NOTICE_PUBLISHED.equals(event.eventType())
				|| event.payloadVersion() != 1
				|| !source.getDeduplicationKey().equals(event.eventId())) {
			throw new IllegalArgumentException("家校通知事件契约不匹配");
		}
	}

	private boolean isFinishedOrNotDue(DataEventConsumption consumption, LocalDateTime now) {
		return "PROCESSED".equals(consumption.getStatus()) || "DEAD".equals(consumption.getStatus())
				|| consumption.getNextAttemptAt() != null && consumption.getNextAttemptAt().isAfter(now);
	}

	private String limit(String message, int maxLength) {
		if (message == null) {
			return "unknown event consumption failure";
		}
		return message.length() <= maxLength ? message : message.substring(0, maxLength);
	}
}
