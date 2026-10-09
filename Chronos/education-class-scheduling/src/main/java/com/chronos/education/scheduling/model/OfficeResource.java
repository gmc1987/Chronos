package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 协同办公资源：车辆或印章。 */
@Entity
@Getter
@Setter
@Table(
		name = "collab_office_resource",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_office_resource_code",
				columnNames = {"resource_type", "resource_code"}))
public class OfficeResource extends BaseEntity {
	@Column(name = "resource_type", length = 24, nullable = false)
	private String resourceType;
	@Column(name = "resource_code", length = 64, nullable = false)
	private String resourceCode;
	@Column(name = "resource_name", length = 128, nullable = false)
	private String resourceName;
	@Column(name = "license_plate", length = 32)
	private String licensePlate;
	@Column(name = "capacity")
	private Integer capacity;
	@Column(name = "description", length = 500)
	private String description;
	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
