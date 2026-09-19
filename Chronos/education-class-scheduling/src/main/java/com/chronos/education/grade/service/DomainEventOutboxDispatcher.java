package com.chronos.education.grade.service;

import com.chronos.education.grade.model.DomainEventOutbox;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Batch HTTP dispatcher for durable domain events. Receivers must be idempotent by eventId. */
@Component
public class DomainEventOutboxDispatcher {
	private final DomainEventOutboxService service;
	private final RestClient client;
	private final String endpoint;
	private final int batchSize;
	private final int maxAttempts;
	private final long leaseSeconds;

	public DomainEventOutboxDispatcher(
			DomainEventOutboxService service,
			@Value("${chronos.domain-events.webhook-url:}") String endpoint,
			@Value("${chronos.domain-events.batch-size:50}") int batchSize,
			@Value("${chronos.domain-events.max-attempts:10}") int maxAttempts,
			@Value("${chronos.domain-events.lease-seconds:120}") long leaseSeconds) {
		this.service = service;
		this.client = RestClient.builder().build();
		this.endpoint = endpoint;
		this.batchSize = batchSize;
		this.maxAttempts = maxAttempts;
		this.leaseSeconds = leaseSeconds;
	}

	@Scheduled(fixedDelayString = "${chronos.domain-events.dispatch-delay-ms:5000}")
	public void dispatch() {
		if (endpoint.isBlank()) {
			return;
		}
		List<DomainEventOutbox> events = service.claimBatch(batchSize, LocalDateTime.now(), leaseSeconds);
		for (DomainEventOutbox event : events) {
			try {
				client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
						.header("Idempotency-Key", event.getDeduplicationKey())
						.body(event.getPayloadJson()).retrieve().toBodilessEntity();
				service.markSent(event.getId());
			} catch (Exception failure) {
				service.markFailed(event.getId(), failure, maxAttempts);
			}
		}
	}
}
