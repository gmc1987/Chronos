package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教师禁排或偏好时间；FORBIDDEN 是硬约束，PREFERRED 是自动排课评分项。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teacher_time_constraint")
public class TeacherTimeConstraint extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "teacher_id", length = 64, nullable = false)
	private String teacherId;

	@Column(name = "day_of_week", nullable = false)
	private Integer dayOfWeek;

	@Column(name = "period_no", nullable = false)
	private Integer periodNo;

	@Column(name = "constraint_type", length = 24, nullable = false)
	private String constraintType = "FORBIDDEN";

	@Column(name = "weight", nullable = false)
	private Integer weight = 10;

	@Column(name = "reason", length = 500)
	private String reason;
}
