package com.chronos.education.academic.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 培养方案课程要求；发布时随培养方案一起固化到快照。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_academic_program_course")
public class AcademicProgramCourse extends BaseEntity {
	@Column(name = "program_id", nullable = false, length = 64)
	private String programId;
	@Column(name = "course_id", nullable = false, length = 64)
	private String courseId;
	@Column(name = "suggested_term_no", nullable = false)
	private Integer suggestedTermNo;
	@Column(name = "course_nature", nullable = false, length = 24)
	private String courseNature;
	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal credits;
	@Column(name = "total_hours", nullable = false)
	private Integer totalHours;
	@Column(name = "theory_hours", nullable = false)
	private Integer theoryHours = 0;
	@Column(name = "practice_hours", nullable = false)
	private Integer practiceHours = 0;
	@Column(name = "prerequisite_course_ids_json", nullable = false, columnDefinition = "text")
	private String prerequisiteCourseIdsJson = "[]";
	@Column(name = "course_group_code", length = 64)
	private String courseGroupCode;
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;
}
