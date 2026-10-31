package com.chronos.education.academic.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 正式选退课台账；教学班成员表是执行投影，不再作为唯一业务事实。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_course_enrollment")
public class CourseEnrollment extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;
	@Column(name = "term_code", nullable = false, length = 32)
	private String termCode;
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(name = "program_course_id", length = 64)
	private String programCourseId;
	@Column(name = "enrollment_source", nullable = false, length = 24)
	private String enrollmentSource = "MANUAL";
	@Column(nullable = false, length = 24)
	private String status = "ENROLLED";
	@Column(name = "enrolled_at", nullable = false)
	private LocalDateTime enrolledAt;
	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;
	@Column(name = "withdrawn_reason", length = 1000)
	private String withdrawnReason;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
