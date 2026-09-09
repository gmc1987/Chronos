package com.chronos.message.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 附件只保存不可猜测的存储键，下载必须重新经过受众或管理权限校验。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "msg_publication_attachment", comment = "通知公告附件")
public class PublicationAttachment extends BaseEntity {
	@Column(name = "publication_id", length = 64, nullable = false)
	private String publicationId;

	@Column(name = "original_name", length = 500, nullable = false)
	private String originalName;

	@Column(name = "content_type", length = 200)
	private String contentType;

	@Column(name = "storage_key", length = 500, nullable = false, unique = true)
	private String storageKey;

	@Column(name = "file_size", nullable = false)
	private Long fileSize;

	@Column(name = "sha256", length = 64, nullable = false)
	private String sha256;

	@Column(name = "primary_content", nullable = false)
	private Boolean primaryContent = false;
}
