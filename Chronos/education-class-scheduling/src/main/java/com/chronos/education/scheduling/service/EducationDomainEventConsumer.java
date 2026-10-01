package com.chronos.education.scheduling.service;

import com.chronos.education.grade.dao.DomainEventOutboxRepository;
import com.chronos.education.grade.model.DomainEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Consumes only published, versioned contracts that are already durably stored in the education outbox.
 * Consumption is an auditable side effect and never calls an external service.
 */
@Service
public class EducationDomainEventConsumer {
	private static final String HOME_NOTICE_PUBLISHED = "HomeNoticePublishedV1";

	private final DomainEventOutboxRepository outbox;
	private final EducationDomainEventConsumptionProcessor processor;
	private final int batchSize;

	public EducationDomainEventConsumer(
			DomainEventOutboxRepository outbox,
			EducationDomainEventConsumptionProcessor processor,
			@Value("${chronos.education.data-center.events.batch-size:100}") int batchSize) {
		this.outbox = outbox;
		this.processor = processor;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${chronos.education.data-center.events.poll-delay-ms:5000}")
	public void consumePublishedEvents() {
		for (DomainEventOutbox event : outbox.findDataCenterCandidates(
				List.of(HOME_NOTICE_PUBLISHED), LocalDateTime.now(), PageRequest.of(0, batchSize))) {
			processor.process(event);
		}
	}
}
