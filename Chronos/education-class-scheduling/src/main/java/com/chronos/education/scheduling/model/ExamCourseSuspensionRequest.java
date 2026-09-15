package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 考试占课的批量审批单，与每条课表日期例外分离。 */
@Entity
@Getter
@Setter
@Table(name = "edu_exam_course_suspension_request")
public class ExamCourseSuspensionRequest extends BaseEntity {
	@Column(name = "plan_id", nullable = false, length = 64)
	private String planId;

	@Column(name = "scope_mode", nullable = false, length = 24)
	private String scopeMode;

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
