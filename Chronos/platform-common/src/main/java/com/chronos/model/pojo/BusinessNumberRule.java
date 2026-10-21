package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 可按学校覆盖的业务编号规则；计数器单独保存，避免修改规则破坏已发编号。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_business_number_rule", uniqueConstraints = @UniqueConstraint(
		name = "uk_business_number_rule_scope", columnNames = {"business_type", "organization_id"}))
public class BusinessNumberRule extends BaseEntity {
	@Column(name = "business_type", length = 32, nullable = false)
	private String businessType;

	@Column(name = "organization_id", length = 64, nullable = false)
	private String organizationId = "GLOBAL";

	@Column(name = "pattern", length = 100, nullable = false)
	private String pattern;

	@Column(name = "sequence_length", nullable = false)
	private Integer sequenceLength;

	@Column(name = "reset_period", length = 16, nullable = false)
	private String resetPeriod = "YEAR";

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	@Column(name = "description", length = 500)
	private String description;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
