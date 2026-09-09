package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 课程所属学科，是排课资格、成绩分析和题库归类的统一维度。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_subject")
public class Subject extends BaseEntity {
	@Column(name = "subject_code", length = 64, nullable = false, unique = true)
	private String subjectCode;

	@Column(name = "subject_name", length = 128, nullable = false)
	private String subjectName;

	@Column(name = "subject_category", length = 32, nullable = false)
	private String subjectCategory;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;
}
