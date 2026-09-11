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
@Table(name = "edu_administrative_class")
public class AdministrativeClass extends BaseEntity {
	@Column(name = "class_code", length = 64, nullable = false, unique = true)
	private String classCode;

	@Column(name = "class_name", length = 128, nullable = false)
	private String className;

	@Column(name = "grade_year", nullable = false)
	private Integer gradeYear;

	@Column(name = "grade_id", length = 64)
	private String gradeId;

	@Column(name = "major_id", length = 64, nullable = false)
	private String majorId;

	@Column(name = "head_teacher_id", length = 64)
	private String headTeacherId;

	@Transient
	private String headTeacherName;

	@Column(name = "campus_id", length = 64)
	private String campusId;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
