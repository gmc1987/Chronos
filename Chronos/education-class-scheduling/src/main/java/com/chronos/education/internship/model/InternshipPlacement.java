package com.chronos.education.internship.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_internship_placement")
public class InternshipPlacement extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;

	@Column(name = "term_code", nullable = false, length = 32)
	private String termCode;

	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;

	@Column(name = "company_id", nullable = false, length = 64)
	private String companyId;

	@Column(name = "position_id", nullable = false, length = 64)
	private String positionId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "agreement_file_id", nullable = false, length = 64)
	private String agreementFileId;

	@Column(name = "consent_file_id", nullable = false, length = 64)
	private String consentFileId;

	@Column(name = "insurance_file_id", nullable = false, length = 64)
	private String insuranceFileId;

	@Column(nullable = false, length = 24)
	private String status = "DRAFT";

	@Column(name = "submitted_by", length = 128)
	private String submittedBy;

	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;

	@Column(name = "approved_by", length = 128)
	private String approvedBy;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "decision_comment", length = 1000)
	private String decisionComment;

	@Column(name = "snapshot_json", columnDefinition = "text")
	private String snapshotJson;

	@Column(name = "snapshot_hash", length = 64)
	private String snapshotHash;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
