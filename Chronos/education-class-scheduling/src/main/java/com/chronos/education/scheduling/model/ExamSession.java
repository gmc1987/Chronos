package com.chronos.education.scheduling.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "edu_exam_session")
public class ExamSession extends BaseEntity {
	@Column(name = "plan_id", nullable = false, length = 64)
	private String planId;

	@Column(name = "subject_id", nullable = false, length = 64)
	private String subjectId;

	@Column(name = "exam_date", nullable = false)
	private LocalDate examDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "DRAFT";
}
