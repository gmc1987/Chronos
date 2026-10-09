package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 用车与用印审批台账。 */
@Entity
@Getter
@Setter
@Table(
		name = "collab_office_request",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_office_request_workflow",
				columnNames = "workflow_instance_id"))
public class OfficeApprovalRequest extends BaseEntity {
	@Column(name = "request_type", length = 24, nullable = false)
	private String requestType;
	@Column(name = "workflow_instance_id", length = 64)
	private String workflowInstanceId;
	@Column(name = "business_key", length = 128, nullable = false)
	private String businessKey;
	@Column(name = "employee_id", length = 64, nullable = false)
	private String employeeId;
	@Column(name = "resource_id", length = 64, nullable = false)
	private String resourceId;
	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;
	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;
	@Column(name = "purpose", length = 1000, nullable = false)
	private String purpose;
	@Column(name = "destination", length = 200)
	private String destination;
	@Column(name = "passenger_count")
	private Integer passengerCount;
	@Column(name = "document_title", length = 300)
	private String documentTitle;
	@Column(name = "copy_count")
	private Integer copyCount;
	@Column(name = "attachment_file_id", length = 64)
	private String attachmentFileId;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "PENDING";
	@Column(name = "approved_by", length = 128)
	private String approvedBy;
	@Column(name = "withdrawn_by", length = 128)
	private String withdrawnBy;
	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;
	@Column(name = "completed_by", length = 128)
	private String completedBy;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
