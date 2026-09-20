package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 教室申请审批完成后的资源占用台账。 */
@Entity
@Getter
@Setter
@Table(
		name = "edu_classroom_reservation",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_classroom_reservation_workflow",
				columnNames = "workflow_instance_id"))
public class ClassroomReservation extends BaseEntity {
	@Column(name = "workflow_instance_id", length = 64, nullable = false)
	private String workflowInstanceId;

	@Column(name = "business_key", length = 128)
	private String businessKey;

	@Column(name = "applicant_username", length = 128, nullable = false)
	private String applicantUsername;

	@Column(name = "applicant_id", length = 64)
	private String applicantId;

	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "classroom_id", length = 64, nullable = false)
	private String classroomId;

	@Column(name = "usage_date", nullable = false)
	private LocalDate usageDate;

	@Column(name = "start_period", nullable = false)
	private Integer startPeriod;

	@Column(name = "duration_periods", nullable = false)
	private Integer durationPeriods;

	@Column(name = "attendee_count", nullable = false)
	private Integer attendeeCount;

	@Column(name = "purpose", length = 1000, nullable = false)
	private String purpose;

	@Column(name = "status", length = 24, nullable = false)
	private String status;

	@Column(name = "approved_by", length = 128)
	private String approvedBy;

	@Column(name = "failure_message", length = 1000)
	private String failureMessage;

	@Column(name = "cancelled_by", length = 128)
	private String cancelledBy;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "cancellation_reason", length = 1000)
	private String cancellationReason;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
