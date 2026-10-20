package com.chronos.education.miniprogram.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Permanent school-specific scene used by the unified WeChat mini program. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_mini_program_school_entry")
public class MiniProgramSchoolEntry extends BaseEntity {
	@Column(name = "organization_id", length = 64, nullable = false, unique = true)
	private String organizationId;

	@Column(name = "scene_code", length = 32, nullable = false, unique = true)
	private String sceneCode;

	@Column(name = "page_path", length = 200, nullable = false)
	private String pagePath = "pages/bind/index";

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";

	@Column(name = "generated_at", nullable = false)
	private LocalDateTime generatedAt;

	@Column(name = "last_used_at")
	private LocalDateTime lastUsedAt;

	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
