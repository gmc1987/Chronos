package com.chronos.message.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 每次保存和关键状态迁移前保存完整 JSON 快照，支持审计与历史恢复。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_publication_version",
		comment = "通知公告历史版本",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_publication_version",
				columnNames = { "publication_id", "version_no" }))
public class PublicationVersion extends BaseEntity {
	@Column(name = "publication_id", length = 64, nullable = false)
	private String publicationId;

	@Column(name = "version_no", nullable = false)
	private Integer versionNo;

	@Column(name = "operation", length = 32, nullable = false)
	private String operation;

	@Column(name = "snapshot_json", columnDefinition = "text", nullable = false)
	private String snapshotJson;
}
