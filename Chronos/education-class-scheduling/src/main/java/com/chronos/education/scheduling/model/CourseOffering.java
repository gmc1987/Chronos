package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 走班教学任务；一个任务代表某学期由教师承担的一门教学班课程。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_course_offering",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_course_offering_code",
				columnNames = { "semester_code", "offering_code" }))
public class CourseOffering extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "offering_code", length = 64, nullable = false)
	private String offeringCode;

	@Column(name = "course_code", length = 64, nullable = false)
	private String courseCode;

	@Column(name = "course_name", length = 128, nullable = false)
	private String courseName;

	@Column(name = "teaching_class_name", length = 128, nullable = false)
	private String teachingClassName;

	@Column(name = "teacher_id", length = 64, nullable = false)
	private String teacherId;

	@Column(name = "teacher_name", length = 128, nullable = false)
	private String teacherName;

	@Column(name = "student_count", nullable = false)
	private Integer studentCount = 0;

	@Column(name = "weekly_lessons", nullable = false)
	private Integer weeklyLessons = 2;

	@Column(name = "preferred_duration_periods", nullable = false)
	private Integer preferredDurationPeriods = 1;

	@Column(name = "week_pattern", length = 16, nullable = false)
	private String weekPattern = "ALL";

	@Column(name = "required_room_type", length = 32)
	private String requiredRoomType;

	@Column(name = "required_equipment_codes", length = 1000)
	private String requiredEquipmentCodes;

	@Column(name = "campus_id", length = 64)
	private String campusId;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
