package com.chronos.education.internship.model;

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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_internship_assessment")
public class InternshipAssessment extends BaseEntity {
	@Column(name = "placement_id", nullable = false, length = 64)
	private String placementId;

	@Column(name = "company_score", nullable = false)
	private BigDecimal companyScore;

	@Column(name = "school_score", nullable = false)
	private BigDecimal schoolScore;

	@Column(name = "final_score", nullable = false)
	private BigDecimal finalScore;

	@Column(length = 2000)
	private String comment;

	@Column(nullable = false, length = 24)
	private String status = "SUBMITTED";

	@Column(name = "submitted_by", nullable = false, length = 128)
	private String submittedBy;

	@Column(name = "approved_by", length = 128)
	private String approvedBy;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
