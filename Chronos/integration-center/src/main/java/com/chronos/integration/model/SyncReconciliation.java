package com.chronos.integration.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "int_sync_reconciliation")
@Getter
@Setter
public class SyncReconciliation extends BaseEntity {
	@Column(name = "run_id", nullable = false, length = 64)
	private String runId;
	@Column(name = "object_type", nullable = false, length = 64)
	private String objectType;
	@Column(name = "source_count", nullable = false)
	private Long sourceCount = 0L;
	@Column(name = "accepted_count", nullable = false)
	private Long acceptedCount = 0L;
	@Column(name = "rejected_count", nullable = false)
	private Long rejectedCount = 0L;
	@Column(name = "missing_count", nullable = false)
	private Long missingCount = 0L;
	@Column(nullable = false, length = 24)
	private String status = "PENDING";
}
