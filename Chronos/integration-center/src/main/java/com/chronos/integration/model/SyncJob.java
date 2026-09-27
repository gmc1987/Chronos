package com.chronos.integration.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "int_sync_job")
@Getter
@Setter
public class SyncJob extends BaseEntity {
	@Column(nullable = false, length = 160)
	private String name;
	@Column(name = "connector_id", nullable = false, length = 64)
	private String connectorId;
	@Column(name = "cron_expression", nullable = false, length = 100)
	private String cronExpression;
	@Column(nullable = false, length = 20)
	private String status = "ENABLED";
	@Column(name = "direction", length = 16)
	private String direction = "INBOUND";
	@Column(name = "batch_size", nullable = false)
	private Integer batchSize = 100;
	@Column(name = "cursor_value", columnDefinition = "text")
	private String cursorValue;
	@Column(name = "object_type", nullable = false, length = 64)
	private String objectType = "DEFAULT";
	@Column(name = "mapping_version_no", nullable = false)
	private Integer mappingVersionNo = 1;
	@Column(name = "request_path", nullable = false, length = 500)
	private String requestPath;
	@Column(name = "idempotency_key_template", length = 300)
	private String idempotencyKeyTemplate;
	@Column(name = "lease_owner", length = 120)
	private String leaseOwner;
	@Column(name = "lease_until")
	private LocalDateTime leaseUntil;
}
