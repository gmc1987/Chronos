package com.chronos.model.pojo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "iam_identity_sync_conflict")
@Getter
@Setter
public class IdentitySyncConflict extends BaseEntity {
	@Column(name = "source_id", nullable = false, length = 64)
	private String sourceId;
	@Column(name = "external_subject", nullable = false, length = 256)
	private String externalSubject;
	@Column(name = "field_name", nullable = false, length = 128)
	private String fieldName;
	@Column(name = "current_value_hash", nullable = false, length = 128)
	private String currentValueHash;
	@Column(name = "incoming_value_hash", nullable = false, length = 128)
	private String incomingValueHash;
	@Column(nullable = false, length = 500)
	private String reason;
	@Column(nullable = false, length = 24)
	private String status = "OPEN";
	@Column(name = "decision_by", length = 128)
	private String decisionBy;
	@Column(name = "decision_at")
	private LocalDateTime decisionAt;
}
