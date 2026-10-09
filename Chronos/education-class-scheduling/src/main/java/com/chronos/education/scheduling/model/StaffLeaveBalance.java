package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 教职工年度假期额度。 */
@Entity
@Getter
@Setter
@Table(
		name = "edu_staff_leave_balance",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_staff_leave_balance",
				columnNames = {"employee_id", "leave_year", "leave_type"}))
public class StaffLeaveBalance extends BaseEntity {
	@Column(name = "employee_id", length = 64, nullable = false)
	private String employeeId;
	@Column(name = "leave_year", nullable = false)
	private Integer leaveYear;
	@Column(name = "leave_type", length = 32, nullable = false)
	private String leaveType;
	@Column(name = "entitlement_days", precision = 8, scale = 2, nullable = false)
	private BigDecimal entitlementDays = BigDecimal.ZERO;
	@Column(name = "carryover_days", precision = 8, scale = 2, nullable = false)
	private BigDecimal carryoverDays = BigDecimal.ZERO;
	@Column(name = "adjustment_days", precision = 8, scale = 2, nullable = false)
	private BigDecimal adjustmentDays = BigDecimal.ZERO;
	@Column(name = "consumed_days", precision = 8, scale = 2, nullable = false)
	private BigDecimal consumedDays = BigDecimal.ZERO;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;

	@Transient
	public BigDecimal getAvailableDays() {
		return entitlementDays.add(carryoverDays).add(adjustmentDays).subtract(consumedDays);
	}
}
