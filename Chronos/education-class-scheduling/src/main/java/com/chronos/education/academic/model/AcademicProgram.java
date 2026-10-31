package com.chronos.education.academic.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 培养方案主版本；发布后内容冻结，新调整必须复制成下一版本。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_academic_program")
public class AcademicProgram extends BaseEntity {
	@Column(name = "school_id", nullable = false, length = 64)
	private String schoolId;
	@Column(name = "major_id", nullable = false, length = 64)
	private String majorId;
	@Column(name = "cohort_year", nullable = false)
	private Integer cohortYear;
	@Column(name = "education_level", nullable = false, length = 24)
	private String educationLevel;
	@Column(name = "program_code", nullable = false, length = 64)
	private String programCode;
	@Column(name = "program_name", nullable = false, length = 200)
	private String programName;
	@Column(name = "version_no", nullable = false)
	private Integer versionNo = 1;
	@Column(name = "minimum_credits", nullable = false, precision = 8, scale = 2)
	private BigDecimal minimumCredits = BigDecimal.ZERO;
	@Column(name = "total_hours", nullable = false)
	private Integer totalHours = 0;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "effective_start_date")
	private LocalDate effectiveStartDate;
	@Column(name = "effective_end_date")
	private LocalDate effectiveEndDate;
	@Column(name = "submitted_by", length = 128)
	private String submittedBy;
	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;
	@Column(name = "published_by", length = 128)
	private String publishedBy;
	@Column(name = "published_at")
	private LocalDateTime publishedAt;
	@Column(name = "snapshot_json", columnDefinition = "text")
	private String snapshotJson;
	@Column(name = "snapshot_hash", length = 64)
	private String snapshotHash;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
