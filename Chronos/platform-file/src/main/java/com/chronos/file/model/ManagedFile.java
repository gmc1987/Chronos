package com.chronos.file.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件元数据与二进制存储解耦，数据库只保存受控引用。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_managed_file")
public class ManagedFile extends BaseEntity {
	@Column(name = "original_name", length = 255, nullable = false)
	private String originalName;
	@Column(name = "storage_key", length = 500, nullable = false, unique = true)
	private String storageKey;
	@Column(name = "content_type", length = 128, nullable = false)
	private String contentType;
	@Column(name = "file_size", nullable = false)
	private Long fileSize;
	@Column(name = "sha256", length = 64, nullable = false)
	private String sha256;
	@Column(name = "owner_username", length = 100, nullable = false)
	private String ownerUsername;
	@Column(name = "business_type", length = 64, nullable = false)
	private String businessType;
	@Column(name = "business_id", length = 128)
	private String businessId;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
