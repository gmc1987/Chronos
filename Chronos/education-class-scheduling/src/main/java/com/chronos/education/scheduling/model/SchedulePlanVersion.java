package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已发布课表的不可变快照；回滚会生成新版本，不会覆盖历史版本。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_schedule_plan_version",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_schedule_plan_version",
				columnNames = { "semester_code", "version_no" }))
public class SchedulePlanVersion extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "version_no", nullable = false)
	private Integer versionNo;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "PUBLISHED";

	@Column(name = "source_version_no")
	private Integer sourceVersionNo;

	@Column(name = "entry_count", nullable = false)
	private Integer entryCount;

	@Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
	private String snapshotJson;

	@Column(name = "published_by", length = 128, nullable = false)
	private String publishedBy;

	@Column(name = "published_at", nullable = false)
	private LocalDateTime publishedAt;
}
