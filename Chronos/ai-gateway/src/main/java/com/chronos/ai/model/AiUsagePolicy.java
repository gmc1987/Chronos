package com.chronos.ai.model;

import java.math.BigDecimal;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Runtime budget and rate policy for the whole platform, one model, or one user. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ai_usage_policy", uniqueConstraints = @UniqueConstraint(
		name = "uk_ai_usage_policy_scope",
		columnNames = { "scope_type", "scope_key" }))
public class AiUsagePolicy extends BaseEntity {
	@Column(name = "scope_type", length = 16, nullable = false)
	private String scopeType;

	@Column(name = "scope_key", length = 128, nullable = false)
	private String scopeKey;

	@Column(name = "requests_per_minute")
	private Integer requestsPerMinute;

	@Column(name = "daily_token_limit")
	private Long dailyTokenLimit;

	@Column(name = "monthly_budget", precision = 18, scale = 6)
	private BigDecimal monthlyBudget;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
}
