package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Durable domain-event delivery record; intentionally separate from wf_outbox. */
@Entity
@Table(name = "edu_domain_event_outbox",
		indexes = @Index(name = "idx_edu_domain_event_outbox_dispatch",
				columnList = "status,next_attempt_at"))
@Getter
@Setter
public class DomainEventOutbox extends BaseEntity {
	@Column(name = "event_type", nullable = false, length = 120)
	private String eventType;

	@Column(name = "aggregate_id", nullable = false, length = 64)
	private String aggregateId;

	@Column(name = "payload_json", nullable = false, columnDefinition = "text")
	private String payloadJson;

	@Column(nullable = false, length = 30)
	private String status = "PENDING";

	@Column(nullable = false)
	private Integer attempts = 0;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(name = "lease_until")
	private LocalDateTime leaseUntil;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "deduplication_key", nullable = false, length = 200, unique = true)
	private String deduplicationKey;
}
