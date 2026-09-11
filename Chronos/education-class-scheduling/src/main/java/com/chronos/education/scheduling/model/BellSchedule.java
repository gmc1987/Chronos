package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 校区在指定学期采用的作息方案。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_bell_schedule")
public class BellSchedule extends BaseEntity {
	@Column(name = "schedule_code", length = 48, nullable = false, unique = true)
	private String scheduleCode;

	@Column(name = "schedule_name", length = 128, nullable = false)
	private String scheduleName;

	@Column(name = "academic_term_id", length = 64, nullable = false)
	private String academicTermId;

	@Column(name = "campus_id", length = 64, nullable = false)
	private String campusId;

	@Column(name = "default_schedule", nullable = false)
	private Boolean defaultSchedule = false;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
