package com.chronos.facility.model;

import java.time.LocalDateTime;

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
@Table(name = "fm_repair_order", comment = "设施故障报修单")
public class RepairOrder extends BaseEntity {
	@Column(name = "order_no", nullable = false, unique = true, length = 32)
	private String orderNo;
	@Column(name = "title", nullable = false, length = 200)
	private String title;
	@Column(name = "category", nullable = false, length = 32)
	private String category;
	@Column(name = "description", nullable = false, columnDefinition = "text")
	private String description;
	@Column(name = "location", nullable = false, length = 300)
	private String location;
	@Column(name = "urgency", nullable = false, length = 16)
	private String urgency = "NORMAL";
	@Column(name = "contact_name", nullable = false, length = 100)
	private String contactName;
	@Column(name = "contact_phone", length = 32)
	private String contactPhone;
	@Column(name = "reporter_username", nullable = false, length = 100)
	private String reporterUsername;
	@Column(name = "reporter_name", nullable = false, length = 100)
	private String reporterName;
	@Column(name = "organization_id", length = 64)
	private String organizationId;
	@Column(name = "status", nullable = false, length = 32)
	private String status = "PENDING_DISPATCH";
	@Column(name = "dispatch_mode", nullable = false, length = 16)
	private String dispatchMode = "AUTO";
	@Column(name = "technician_id", length = 64)
	private String technicianId;
	@Column(name = "technician_username", length = 100)
	private String technicianUsername;
	@Column(name = "technician_name", length = 100)
	private String technicianName;
	@Column(name = "dispatched_at")
	private LocalDateTime dispatchedAt;
	@Column(name = "dispatch_deadline")
	private LocalDateTime dispatchDeadline;
	@Column(name = "next_dispatch_retry_at")
	private LocalDateTime nextDispatchRetryAt;
	@Column(name = "dispatch_attempts", nullable = false)
	private Integer dispatchAttempts = 0;
	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;
	@Column(name = "started_at")
	private LocalDateTime startedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Column(name = "closed_at")
	private LocalDateTime closedAt;
	@Column(name = "completion_note", length = 2000)
	private String completionNote;
	@Column(name = "close_comment", length = 1000)
	private String closeComment;
	@Column(name = "rating")
	private Integer rating;
	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
