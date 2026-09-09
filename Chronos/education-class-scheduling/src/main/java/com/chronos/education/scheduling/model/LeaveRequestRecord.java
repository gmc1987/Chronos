package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 教师和学生请假的统一业务台账。 */
@Entity
@Getter
@Setter
@Table(
		name = "edu_leave_request",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_leave_workflow_instance",
				columnNames = "workflow_instance_id"))
public class LeaveRequestRecord extends BaseEntity {
	@Column(name = "workflow_instance_id", length = 64, nullable = false)
	private String workflowInstanceId;
	@Column(name = "business_key", length = 128)
	private String businessKey;
	@Column(name = "applicant_type", length = 16, nullable = false)
	private String applicantType;
	@Column(name = "applicant_id", length = 64, nullable = false)
	private String applicantId;
	@Column(name = "leave_type", length = 32, nullable = false)
	private String leaveType;
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;
	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;
	@Column(name = "reason", length = 1000, nullable = false)
	private String reason;
	@Column(name = "status", length = 24, nullable = false)
	private String status;
	@Column(name = "approved_by", length = 128)
	private String approvedBy;
}
