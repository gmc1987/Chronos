package com.chronos.education.internship.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_internship_company")
public class InternshipCompany extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;

	@Column(name = "company_code", nullable = false, length = 64)
	private String companyCode;

	@Column(name = "company_name", nullable = false, length = 200)
	private String companyName;

	@Column(name = "credit_code", length = 64)
	private String creditCode;

	@Column(name = "contact_name", length = 100)
	private String contactName;

	@Column(name = "contact_phone", length = 32)
	private String contactPhone;

	@Column(length = 500)
	private String address;

	@Column(name = "risk_level", nullable = false, length = 16)
	private String riskLevel = "LOW";

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

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
