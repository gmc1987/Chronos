package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 学校年级主数据，不使用入学年份临时替代年级实体。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_grade")
public class EducationGrade extends BaseEntity {
	@Column(name = "grade_code", length = 64, nullable = false, unique = true)
	private String gradeCode;

	@Column(name = "grade_name", length = 128, nullable = false)
	private String gradeName;

	@Column(name = "enrollment_year", nullable = false)
	private Integer enrollmentYear;

	@Column(name = "education_stage", length = 32, nullable = false)
	private String educationStage;

	@Column(name = "director_teacher_id", length = 64)
	private String directorTeacherId;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;
}
