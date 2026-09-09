package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排课 Agent 生成的约束草稿。草稿与正式约束分表保存，未经人工确认绝不影响排课结果。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_scheduling_agent_proposal")
public class SchedulingAgentProposal extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "request_text", columnDefinition = "text", nullable = false)
	private String requestText;

	@Column(name = "teacher_id", length = 64, nullable = false)
	private String teacherId;

	@Column(name = "teacher_name", length = 128, nullable = false)
	private String teacherName;

	@Column(name = "day_of_week", nullable = false)
	private Integer dayOfWeek;

	@Column(name = "period_no", nullable = false)
	private Integer periodNo;

	@Column(name = "constraint_type", length = 24, nullable = false)
	private String constraintType;

	@Column(name = "reason", length = 500)
	private String reason;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "DRAFT";

	@Column(name = "confirmed_by", length = 128)
	private String confirmedBy;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Column(name = "applied_constraint_id", length = 64)
	private String appliedConstraintId;
}
