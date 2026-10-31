package com.chronos.education.academic.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 毕业资格审核快照；审批后不随课程或成绩的后续变化重算。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_graduation_audit")
public class GraduationAudit extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;
	@Column(name = "program_id", nullable = false, length = 64)
	private String programId;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "required_credits", nullable = false, precision = 8, scale = 2)
	private BigDecimal requiredCredits;
	@Column(name = "earned_credits", nullable = false, precision = 8, scale = 2)
	private BigDecimal earnedCredits;
	@Column(name = "unmet_requirements_json", nullable = false, columnDefinition = "text")
	private String unmetRequirementsJson = "[]";
	@Column(name = "evidence_snapshot_json", nullable = false, columnDefinition = "text")
	private String evidenceSnapshotJson;
	@Column(name = "snapshot_hash", nullable = false, length = 64)
	private String snapshotHash;
	@Column(name = "reviewed_by", length = 128)
	private String reviewedBy;
	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;
	@Column(name = "approved_by", length = 128)
	private String approvedBy;
	@Column(name = "approved_at")
	private LocalDateTime approvedAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
