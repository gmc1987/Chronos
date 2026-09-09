package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_teaching_class_member",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_teaching_class_member",
				columnNames = { "offering_id", "student_id" }))
public class TeachingClassMember extends BaseEntity {
	@Column(name = "offering_id", length = 64, nullable = false)
	private String offeringId;

	@Column(name = "student_id", length = 64, nullable = false)
	private String studentId;

	@Column(name = "enrollment_status", length = 24, nullable = false)
	private String enrollmentStatus = "ENROLLED";

	@Column(name = "enrolled_at", nullable = false)
	private LocalDateTime enrolledAt;

	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;
}
