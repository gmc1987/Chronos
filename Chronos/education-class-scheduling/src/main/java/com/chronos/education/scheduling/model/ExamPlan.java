package com.chronos.education.scheduling.model;

import java.time.LocalDate;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "edu_exam_plan")
public class ExamPlan extends BaseEntity {
	@Column(name = "semester_code", nullable = false, length = 32)
	private String semesterCode;

	@Column(name = "plan_name", nullable = false, length = 128)
	private String planName;

	@Column(name = "exam_type", nullable = false, length = 32)
	private String examType;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "DRAFT";

	@Column(name = "rule_json", columnDefinition = "text")
	private String ruleJson;
}
