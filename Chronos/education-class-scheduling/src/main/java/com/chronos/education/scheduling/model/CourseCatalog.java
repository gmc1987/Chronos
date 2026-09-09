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
@Table(name = "edu_course_catalog")
public class CourseCatalog extends BaseEntity {
	@Column(name = "course_code", length = 64, nullable = false, unique = true)
	private String courseCode;

	@Column(name = "course_name", length = 128, nullable = false)
	private String courseName;

	@Column(name = "subject_id", length = 64)
	private String subjectId;

	@Column(name = "course_category", length = 32, nullable = false)
	private String courseCategory = "PROFESSIONAL";

	@Column(name = "course_nature", length = 32, nullable = false)
	private String courseNature = "REQUIRED";

	@Column(name = "total_hours", nullable = false)
	private Integer totalHours;

	@Column(name = "theory_hours", nullable = false)
	private Integer theoryHours = 0;

	@Column(name = "practice_hours", nullable = false)
	private Integer practiceHours = 0;

	@Column(name = "credits", precision = 6, scale = 2)
	private java.math.BigDecimal credits;

	@Column(name = "required_room_type", length = 32)
	private String requiredRoomType;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
}
