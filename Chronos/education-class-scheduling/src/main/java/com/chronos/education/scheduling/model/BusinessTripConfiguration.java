package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 出差业务全局配置。 */
@Entity
@Getter
@Setter
@Table(
		name = "edu_business_trip_config",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_business_trip_config_code",
				columnNames = "config_code"))
public class BusinessTripConfiguration extends BaseEntity {
	@Column(name = "config_code", length = 64, nullable = false)
	private String configCode = "DEFAULT";
	@Column(name = "finance_required", nullable = false)
	private Boolean financeRequired = false;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
