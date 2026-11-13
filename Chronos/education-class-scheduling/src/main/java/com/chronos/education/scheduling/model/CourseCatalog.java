package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.util.Set;
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
	@Column(name = "organization_id", length = 64)
	private String organizationId;

	@Column(name = "course_code", length = 64, nullable = false, unique = true)
	private String courseCode;

	@Column(name = "course_name", length = 128, nullable = false)
	private String courseName;

	@Column(name = "subject_id", length = 64)
	private String subjectId;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "edu_course_catalog_major", joinColumns = @JoinColumn(name = "course_id"))
	@Column(name = "major_id", nullable = false, length = 64)
	private Set<String> majorIds;

	public boolean appliesToMajor(String majorId) {
		return "GENERAL".equals(courseCategory) || (majorIds != null && majorIds.contains(majorId));
	}

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
