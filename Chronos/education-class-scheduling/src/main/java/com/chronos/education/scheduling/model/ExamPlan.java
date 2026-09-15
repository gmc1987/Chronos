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

	@Column(name = "base_invigilators", nullable = false)
	private Integer baseInvigilators = 2;

	@Column(name = "extra_staff_threshold", nullable = false)
	private Integer extraStaffThreshold = 60;

	@Column(name = "allow_own_class_invigilation", nullable = false)
	private Boolean allowOwnClassInvigilation = false;

	@Column(name = "rule_json", columnDefinition = "text")
	private String ruleJson;

	@Column(name = "max_consecutive_duties", nullable = false)
	private Integer maxConsecutiveDuties = 2;

	@Column(name = "campus_travel_minutes", nullable = false)
	private Integer campusTravelMinutes = 60;

	@Column(name = "require_subject_qualification", nullable = false)
	private Boolean requireSubjectQualification = false;

	@Column(name = "published_version", nullable = false)
	private Integer publishedVersion = 0;
}
