package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教师任教关系用于明确某学期教师承担的学科、年级和班级范围。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teacher_teaching_assignment")
public class TeacherTeachingAssignment extends BaseEntity {
	@Column(name = "academic_term_id", length = 64, nullable = false)
	private String academicTermId;

	@Column(name = "teacher_id", length = 64, nullable = false)
	private String teacherId;

	@Column(name = "subject_id", length = 64, nullable = false)
	private String subjectId;

	@Column(name = "grade_id", length = 64)
	private String gradeId;

	@Column(name = "administrative_class_id", length = 64)
	private String administrativeClassId;

	@Column(name = "assignment_role", length = 32, nullable = false)
	private String assignmentRole = "TEACHER";

	@Column(name = "weekly_lessons", nullable = false)
	private Integer weeklyLessons = 0;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
}
