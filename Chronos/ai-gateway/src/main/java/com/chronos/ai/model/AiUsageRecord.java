package com.chronos.ai.model;

import java.math.BigDecimal;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Auditable token and cost record for every attempted AI request. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ai_usage_record")
public class AiUsageRecord extends BaseEntity {
	@Column(name = "request_id", length = 64, nullable = false, unique = true)
	private String requestId;

	@Column(name = "model_id", length = 64, nullable = false)
	private String modelId;

	@Column(name = "username", length = 128, nullable = false)
	private String username;

	@Column(name = "operation", length = 32, nullable = false)
	private String operation;

	@Column(name = "status", length = 24, nullable = false)
	private String status;

	@Column(name = "input_tokens", nullable = false)
	private Integer inputTokens = 0;

	@Column(name = "output_tokens", nullable = false)
	private Integer outputTokens = 0;

	@Column(name = "total_tokens", nullable = false)
	private Integer totalTokens = 0;

	@Column(name = "token_estimated", nullable = false)
	private Boolean tokenEstimated = true;

	@Column(name = "cost_amount", precision = 18, scale = 8, nullable = false)
	private BigDecimal costAmount = BigDecimal.ZERO;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "error_code", length = 64)
	private String errorCode;
}
