package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "edu_exam_invigilation_change")
public class ExamInvigilationChange {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(name = "assignment_id", nullable = false, length = 64)
	private String assignmentId;

	@Column(name = "proposed_teacher_id", length = 64)
	private String proposedTeacherId;

	@Column(name = "reason", nullable = false, length = 1000)
	private String reason;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "PENDING";

	@Column(name = "emergency", nullable = false)
	private Boolean emergency = false;

	@Column(name = "requested_by", nullable = false, length = 128)
	private String requestedBy;

	@Column(name = "decided_by", length = 128)
	private String decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "create_time", nullable = false)
	private LocalDateTime createTime = LocalDateTime.now();
}
