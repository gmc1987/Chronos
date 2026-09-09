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
@Table(name = "edu_major")
public class Major extends BaseEntity {
	@Column(name = "major_code", length = 64, nullable = false, unique = true)
	private String majorCode;

	@Column(name = "major_name", length = 128, nullable = false)
	private String majorName;

	@Column(name = "schooling_years", nullable = false)
	private Integer schoolingYears = 3;

	@Column(name = "department_id", length = 64)
	private String departmentId;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
}
