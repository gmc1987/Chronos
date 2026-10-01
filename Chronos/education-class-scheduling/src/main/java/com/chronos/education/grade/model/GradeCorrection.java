package com.chronos.education.grade.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_grade_correction")
public class GradeCorrection extends BaseEntity {
	@Column(name = "gradebook_id", nullable = false, length = 64)
	private String gradebookId;
	@Column(name = "base_version", nullable = false)
	private Integer baseVersion;
	@Column(name = "target_version", nullable = false)
	private Integer targetVersion;
	// PostgreSQL JSON 文本统一映射为 text，避免 @Lob 被 Hibernate 解释为 oid。
	@Column(name = "correction_json", nullable = false, columnDefinition = "text")
	private String correctionJson;
	@Column(nullable = false, length = 24)
	private String status;
	@Column(name = "requested_by", nullable = false, length = 128)
	private String requestedBy;
	@Column(name = "published_by", length = 128)
	private String publishedBy;
	@Column(length = 500)
	private String reason;
}
