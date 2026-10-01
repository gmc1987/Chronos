package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Idempotent data-center projection of published grade facts. */
@Entity
@Getter
@Setter
@Table(name = "data_grade_event_fact")
public class DataGradeEventFact extends BaseEntity {
	@Column(name = "event_id", nullable = false, unique = true, length = 128)
	private String eventId;
	@Column(name = "event_type", nullable = false, length = 80)
	private String eventType;
	@Column(name = "aggregate_id", nullable = false, length = 64)
	private String aggregateId;
	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;
	@Column(name = "offering_id", length = 64)
	private String offeringId;
	@Column(name = "student_id", length = 64)
	private String studentId;
	@Column(name = "score", precision = 18, scale = 4)
	private BigDecimal score;
	@Column(name = "max_score", precision = 18, scale = 4)
	private BigDecimal maxScore;
	@Column(name = "source_version", nullable = false, length = 32)
	private String sourceVersion;
}
