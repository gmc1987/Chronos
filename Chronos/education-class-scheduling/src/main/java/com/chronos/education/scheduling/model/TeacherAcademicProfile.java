package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teacher_profile")
public class TeacherAcademicProfile extends BaseEntity {
	@Column(name = "employee_id", length = 64, nullable = false, unique = true)
	private String employeeId;

	@Column(name = "teacher_no", length = 64, nullable = false, unique = true)
	private String teacherNo;

	@Column(name = "teacher_name", length = 128, nullable = false)
	private String teacherName;

	@Column(name = "department_id", length = 64)
	private String departmentId;

	@Column(name = "specialty", length = 128)
	private String specialty;

	@Column(name = "max_weekly_lessons", nullable = false)
	private Integer maxWeeklyLessons = 20;

	@Column(name = "max_daily_lessons", nullable = false)
	private Integer maxDailyLessons = 6;

	@Column(name = "max_consecutive_lessons", nullable = false)
	private Integer maxConsecutiveLessons = 4;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	@Transient
	private String accountUsername;
	@Transient
	private String accountStatus;
}
