package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_student_profile")
public class StudentProfile extends BaseEntity {
	@Column(name = "student_no", length = 64, nullable = false, unique = true)
	private String studentNo;

	@Column(name = "student_name", length = 128, nullable = false)
	private String studentName;

	@Column(name = "gender", length = 16)
	private String gender;

	@Column(name = "grade_year", nullable = false)
	private Integer gradeYear;

	@Column(name = "grade_id", length = 64)
	private String gradeId;

	@Column(name = "major_id", length = 64, nullable = false)
	private String majorId;

	@Column(name = "administrative_class_id", length = 64, nullable = false)
	private String administrativeClassId;

	@Column(name = "enrollment_status", length = 24, nullable = false)
	private String enrollmentStatus = "ACTIVE";

	@Column(name = "phone", length = 32)
	private String phone;
}
