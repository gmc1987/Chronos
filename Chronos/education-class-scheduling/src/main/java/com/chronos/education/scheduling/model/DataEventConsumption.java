package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Durable audit record for data-center consumption of a published domain event. */
@Entity
@Table(name = "data_event_consumption",
		uniqueConstraints = @UniqueConstraint(name = "uk_data_event_consumption_event", columnNames = "event_id"))
@Getter
@Setter
public class DataEventConsumption extends BaseEntity {
	@Column(name = "event_id", nullable = false, length = 200)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 120)
	private String eventType;

	@Column(name = "aggregate_id", nullable = false, length = 64)
	private String aggregateId;

	@Column(name = "payload_json", nullable = false, columnDefinition = "text")
	private String payloadJson;

	@Column(nullable = false, length = 24)
	private String status = "PENDING";

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;
}
