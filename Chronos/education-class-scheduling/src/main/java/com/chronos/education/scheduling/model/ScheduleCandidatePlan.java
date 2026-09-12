package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 自动排课候选快照。生成和比较候选方案不会修改当前草稿课表。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_schedule_candidate_plan")
public class ScheduleCandidatePlan extends BaseEntity {
	@Version
	@Column(name = "lock_version", nullable = false)
	private Long lockVersion = 0L;

	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "plan_name", length = 128, nullable = false)
	private String planName;

	@Column(name = "generation_mode", length = 16, nullable = false)
	private String generationMode;

	@Column(name = "scope_json", columnDefinition = "text", nullable = false)
	private String scopeJson;

	@Column(name = "baseline_hash", length = 64, nullable = false)
	private String baselineHash;

	@Column(name = "snapshot_json", columnDefinition = "text", nullable = false)
	private String snapshotJson;

	@Column(name = "metrics_json", columnDefinition = "text", nullable = false)
	private String metricsJson;

	@Column(name = "entry_count", nullable = false)
	private Integer entryCount;

	@Column(name = "unscheduled_lessons", nullable = false)
	private Integer unscheduledLessons;

	@Column(name = "total_score", nullable = false)
	private Integer totalScore;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "CANDIDATE";

	@Column(name = "review_status", length = 24, nullable = false)
	private String reviewStatus = "DRAFT";

	@Column(name = "owner_username", length = 128, nullable = false)
	private String ownerUsername;

	@Column(name = "collaboration_remark", length = 1000)
	private String collaborationRemark;

	@Column(name = "reviewed_by", length = 128)
	private String reviewedBy;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "review_comment", length = 1000)
	private String reviewComment;

	@Column(name = "generated_by", length = 128, nullable = false)
	private String generatedBy;

	@Column(name = "generated_at", nullable = false)
	private LocalDateTime generatedAt;

	@Column(name = "applied_by", length = 128)
	private String appliedBy;

	@Column(name = "applied_at")
	private LocalDateTime appliedAt;
}
