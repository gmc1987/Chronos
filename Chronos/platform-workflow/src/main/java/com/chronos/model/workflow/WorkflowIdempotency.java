package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "wf_idempotency", uniqueConstraints = @UniqueConstraint(name = "uk_wf_idempotency",
		columnNames = { "actor", "operation", "idempotency_key" }))
@Getter
@Setter
public class WorkflowIdempotency extends BaseEntity {
	@Column(nullable = false, length = 128)
	private String actor;

	@Column(nullable = false, length = 64)
	private String operation;

	@Column(name = "idempotency_key", nullable = false, length = 160)
	private String idempotencyKey;

	@Column(name = "resource_id", length = 64)
	private String resourceId;

	@Column(nullable = false, length = 30)
	private String status = "PROCESSING";

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;
}
