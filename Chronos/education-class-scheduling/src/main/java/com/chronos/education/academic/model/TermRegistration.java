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

/** 学生学期注册记录；选课必须以已确认注册记录为前置。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_term_registration")
public class TermRegistration extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;
	@Column(name = "term_code", nullable = false, length = 32)
	private String termCode;
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;
	@Column(name = "program_id", nullable = false, length = 64)
	private String programId;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "eligibility_snapshot_json", nullable = false, columnDefinition = "text")
	private String eligibilitySnapshotJson;
	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;
	@Column(name = "confirmed_by", length = 128)
	private String confirmedBy;
	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;
	@Column(name = "rejected_reason", length = 1000)
	private String rejectedReason;
	@Column(name = "cancelled_reason", length = 1000)
	private String cancelledReason;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
