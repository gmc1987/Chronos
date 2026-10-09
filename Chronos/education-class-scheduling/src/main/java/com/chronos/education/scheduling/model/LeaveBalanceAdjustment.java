package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 假期额度调整流水，用于追溯人工调整和业务返还。 */
@Entity
@Getter
@Setter
@Table(name = "edu_leave_balance_adjustment")
public class LeaveBalanceAdjustment extends BaseEntity {
	@Column(name = "employee_id", length = 64, nullable = false)
	private String employeeId;
	@Column(name = "leave_year", nullable = false)
	private Integer leaveYear;
	@Column(name = "leave_type", length = 32, nullable = false)
	private String leaveType;
	@Column(name = "change_days", precision = 8, scale = 2, nullable = false)
	private BigDecimal changeDays;
	@Column(name = "reason", length = 500, nullable = false)
	private String reason;
	@Column(name = "source_type", length = 32, nullable = false)
	private String sourceType;
	@Column(name = "source_id", length = 64)
	private String sourceId;
	@Column(name = "operator_username", length = 128, nullable = false)
	private String operatorUsername;
}
