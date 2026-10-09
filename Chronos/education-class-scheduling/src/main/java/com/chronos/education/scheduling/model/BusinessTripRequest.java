package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 教职工出差申请及财务协同状态。 */
@Entity
@Getter
@Setter
@Table(
		name = "edu_business_trip",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_business_trip_workflow",
				columnNames = "workflow_instance_id"))
public class BusinessTripRequest extends BaseEntity {
	@Column(name = "workflow_instance_id", length = 64, nullable = false)
	private String workflowInstanceId;
	@Column(name = "business_key", length = 128, nullable = false)
	private String businessKey;
	@Column(name = "employee_id", length = 64, nullable = false)
	private String employeeId;
	@Column(name = "destination", length = 200, nullable = false)
	private String destination;
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;
	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;
	@Column(name = "purpose", length = 1000, nullable = false)
	private String purpose;
	@Column(name = "budget_project_code", length = 100)
	private String budgetProjectCode;
	@Column(name = "cost_center_code", length = 100)
	private String costCenterCode;
	@Column(name = "currency", length = 3, nullable = false)
	private String currency = "CNY";
	@Column(name = "transport_amount", precision = 18, scale = 2, nullable = false)
	private BigDecimal transportAmount = BigDecimal.ZERO;
	@Column(name = "accommodation_amount", precision = 18, scale = 2, nullable = false)
	private BigDecimal accommodationAmount = BigDecimal.ZERO;
	@Column(name = "meal_amount", precision = 18, scale = 2, nullable = false)
	private BigDecimal mealAmount = BigDecimal.ZERO;
	@Column(name = "other_amount", precision = 18, scale = 2, nullable = false)
	private BigDecimal otherAmount = BigDecimal.ZERO;
	@Column(name = "estimated_amount", precision = 18, scale = 2, nullable = false)
	private BigDecimal estimatedAmount = BigDecimal.ZERO;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "PENDING";
	@Column(name = "finance_status", length = 32, nullable = false)
	private String financeStatus = "NOT_REQUIRED";
	@Column(name = "finance_reference", length = 128)
	private String financeReference;
	@Column(name = "approved_by", length = 128)
	private String approvedBy;
	@Column(name = "cancel_reason", length = 1000)
	private String cancelReason;
	@Column(name = "cancelled_by", length = 128)
	private String cancelledBy;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "withdrawn_by", length = 128)
	private String withdrawnBy;
	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
