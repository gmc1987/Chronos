package com.chronos.model.domain;

import java.math.BigDecimal;
import java.util.Set;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.chronos.model.domain.enums.ResourceStatus;
import com.chronos.model.domain.enums.ResourceType;
import com.chronos.model.pojo.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排程资源聚合根。
 * 组织、组织单元和人员身份由 platform-iam 统一维护，本实体只保存主数据 ID。
 * 对于设备、房间和车辆等非人员资源，externalResourceId 可为空。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_resource", comment = "领域-可排程资源")
public class SchedulableResource extends BaseEntity {

	@Column(name = "organization_id", nullable = false, length = 64)
	private String organizationId;

	@Column(name = "organization_unit_id", length = 64)
	private String organizationUnitId;

	/** IAM 人员/资源 ID；设备、房间、车辆等可为空。 */
	@Column(name = "external_resource_id", length = 64)
	private String externalResourceId;

	@Column(name = "resource_name", length = 100, nullable = false)
	private String resourceName;

	@Column(name = "resource_code", length = 100, nullable = false)
	private String resourceCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 32)
	private ResourceType resourceType;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_status", nullable = false, length = 32)
	private ResourceStatus resourceStatus = ResourceStatus.ACTIVE;

	@Column(name = "calendar_ref", length = 128)
	private String calendarRef;

	@Column(name = "cost_per_hour", precision = 12, scale = 2)
	private BigDecimal costPerHour;

	@Column(name = "active", nullable = false)
	private Boolean active = true;

	@JsonIgnore
	@OneToMany(mappedBy = "resource", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private Set<ResourceCapability> capabilities;

	public void assignToUnit(String organizationUnitId) {
		this.organizationUnitId = organizationUnitId;
	}

	public void activate() {
		this.active = true;
		this.resourceStatus = ResourceStatus.ACTIVE;
	}

	public void deactivate() {
		this.active = false;
		if (this.resourceStatus == ResourceStatus.ACTIVE) this.resourceStatus = ResourceStatus.INACTIVE;
	}

	public void markMaintenance() { this.resourceStatus = ResourceStatus.MAINTENANCE; }

	public void retire() {
		this.active = false;
		this.resourceStatus = ResourceStatus.RETIRED;
	}

	public boolean isSchedulable() {
		return Boolean.TRUE.equals(this.active) && this.resourceStatus == ResourceStatus.ACTIVE;
	}
}
