package com.chronos.education.scheduling.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 已发布考试的版本化变更审批记录，原值和目标值均留痕。 */
@Entity
@Getter
@Setter
@Table(name = "edu_exam_published_change")
public class ExamPublishedChange extends BaseEntity {
	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;

	@Column(name = "plan_id", nullable = false, length = 64)
	private String planId;

	@Column(name = "change_type", nullable = false, length = 24)
	private String changeType;

	@Column(name = "session_id", length = 64)
	private String sessionId;

	@Column(name = "room_id", length = 64)
	private String roomId;

	@Column(name = "old_exam_date")
	private LocalDate oldExamDate;

	@Column(name = "new_exam_date")
	private LocalDate newExamDate;

	@Column(name = "old_start_time")
	private LocalTime oldStartTime;

	@Column(name = "new_start_time")
	private LocalTime newStartTime;

	@Column(name = "old_end_time")
	private LocalTime oldEndTime;

	@Column(name = "new_end_time")
	private LocalTime newEndTime;

	@Column(name = "old_classroom_id", length = 64)
	private String oldClassroomId;

	@Column(name = "new_classroom_id", length = 64)
	private String newClassroomId;

	@Column(name = "base_plan_version", nullable = false)
	private Integer basePlanVersion;

	@Column(name = "applied_plan_version")
	private Integer appliedPlanVersion;

	@Column(name = "reason", nullable = false, length = 1000)
	private String reason;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "PENDING";

	@Column(name = "requested_by", nullable = false, length = 128)
	private String requestedBy;

	@Column(name = "decided_by", length = 128)
	private String decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;
}
