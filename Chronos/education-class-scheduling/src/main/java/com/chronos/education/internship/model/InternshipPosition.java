package com.chronos.education.internship.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_internship_position")
public class InternshipPosition extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;

	@Column(name = "company_id", nullable = false, length = 64)
	private String companyId;

	@Column(name = "position_name", nullable = false, length = 200)
	private String positionName;

	@Column(nullable = false)
	private Integer capacity;

	@Column(name = "mentor_name", length = 100)
	private String mentorName;

	@Column(name = "mentor_phone", length = 32)
	private String mentorPhone;

	@Column(name = "safety_requirements", length = 2000)
	private String safetyRequirements;

	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
