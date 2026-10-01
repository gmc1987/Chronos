package com.chronos.education.meeting.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** Idempotent local intent; an integration adapter may claim and dispatch it later. */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_calendar_outbox")
public class CalendarOutbox extends BaseEntity {
	@Column(name = "idempotency_key", nullable = false, length = 160, unique = true)
	private String idempotencyKey;

	@Column(name = "meeting_id", nullable = false, length = 64)
	private String meetingId;

	@Column(name = "occurrence_key", nullable = false, length = 80)
	private String occurrenceKey;

	@Column(name = "event_type", nullable = false, length = 24)
	private String operation;

	@Column(nullable = false, length = 24)
	private String status = "PENDING";

	@Column(name = "attempt_count", nullable = false)
	private Integer attemptCount = 0;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "participant_username", nullable = false, length = 128)
	private String participantUsername;

	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;
}
