package com.chronos.education.scheduling.model;

import java.time.LocalDate;

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
@Table(name = "edu_academic_term")
public class AcademicTerm extends BaseEntity {
	@Column(name = "term_code", length = 32, nullable = false, unique = true)
	private String termCode;

	@Column(name = "term_name", length = 128, nullable = false)
	private String termName;

	@Column(name = "academic_year", length = 16, nullable = false)
	private String academicYear;

	@Column(name = "term_no", nullable = false)
	private Integer termNo;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "week_count", nullable = false)
	private Integer weekCount = 20;

	@Column(name = "current_term", nullable = false)
	private Boolean currentTerm = false;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
