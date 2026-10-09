package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 文件中心共享记录，二进制仍由 platform-file 管理。 */
@Entity
@Getter
@Setter
@Table(name = "collab_file_share")
public class CollaborationFileShare extends BaseEntity {
	@Column(name = "owner_username", length = 128, nullable = false)
	private String ownerUsername;
	@Column(name = "title", length = 255, nullable = false)
	private String title;
	@Column(name = "description", length = 1000)
	private String description;
	@Column(name = "file_id", length = 64)
	private String fileId;
	@Column(name = "audience_type", length = 24, nullable = false)
	private String audienceType = "PRIVATE";
	@Column(name = "audience_value", length = 500)
	private String audienceValue;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
