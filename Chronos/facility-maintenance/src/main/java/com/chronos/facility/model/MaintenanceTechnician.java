package com.chronos.facility.model;

import java.time.LocalTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_technician", comment = "设施维修人员")
public class MaintenanceTechnician extends BaseEntity {
	@Column(name = "employee_id", nullable = false, unique = true, length = 64)
	private String employeeId;
	@Column(name = "username", nullable = false, unique = true, length = 100)
	private String username;
	@Column(name = "employee_code", nullable = false, length = 100)
	private String employeeCode;
	@Column(name = "technician_name", nullable = false, length = 100)
	private String technicianName;
	@Column(name = "phone", length = 32)
	private String phone;
	@Column(name = "skill_categories", nullable = false, length = 500)
	private String skillCategories;
	@Column(name = "work_status", nullable = false, length = 16)
	private String workStatus = "AVAILABLE";
	@Column(name = "active", nullable = false)
	private Boolean active = true;
	@Column(name = "auto_dispatch", nullable = false)
	private Boolean autoDispatch = true;
	@Column(name = "current_load", nullable = false)
	private Integer currentLoad = 0;
	@Column(name = "max_concurrent", nullable = false)
	private Integer maxConcurrent = 5;
	@Column(name = "responsibility_organization_ids", nullable = false, length = 1000)
	private String responsibilityOrganizationIds = "ALL";
	@Column(name = "responsibility_areas", nullable = false, length = 1000)
	private String responsibilityAreas = "ALL";
	@Column(name = "duty_days", nullable = false, length = 100)
	private String dutyDays = "ALL";
	@Column(name = "duty_start_time", nullable = false)
	private LocalTime dutyStartTime = LocalTime.MIN;
	@Column(name = "duty_end_time", nullable = false)
	private LocalTime dutyEndTime = LocalTime.of(23, 59, 59);
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
