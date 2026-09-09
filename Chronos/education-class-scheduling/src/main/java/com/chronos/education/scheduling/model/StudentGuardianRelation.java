package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 学生与家长为多对多关系，主监护人和紧急联系人属于关系属性。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_student_guardian",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_student_guardian",
				columnNames = { "student_id", "parent_id" }))
public class StudentGuardianRelation extends BaseEntity {
	@Column(name = "student_id", length = 64, nullable = false)
	private String studentId;

	@Column(name = "parent_id", length = 64, nullable = false)
	private String parentId;

	@Column(name = "relationship", length = 32, nullable = false)
	private String relationship;

	@Column(name = "primary_guardian", nullable = false)
	private Boolean primaryGuardian = false;

	@Column(name = "emergency_contact", nullable = false)
	private Boolean emergencyContact = false;
}
