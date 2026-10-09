package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 公文生命周期台账。 */
@Entity
@Getter
@Setter
@Table(name = "collab_official_document")
public class OfficialDocument extends BaseEntity {
	@Column(name = "direction", length = 16, nullable = false)
	private String direction;
	@Column(name = "title", length = 300, nullable = false)
	private String title;
	@Column(name = "document_number", length = 100)
	private String documentNumber;
	@Column(name = "urgency", length = 24, nullable = false)
	private String urgency = "NORMAL";
	@Column(name = "summary", length = 2000)
	private String summary;
	@Column(name = "drafter_username", length = 128, nullable = false)
	private String drafterUsername;
	@Column(name = "workflow_instance_id", length = 64)
	private String workflowInstanceId;
	@Column(name = "business_key", length = 128)
	private String businessKey;
	@Column(name = "primary_file_id", length = 64)
	private String primaryFileId;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "DRAFT";
	@Column(name = "issued_at")
	private LocalDateTime issuedAt;
	@Column(name = "archived_at")
	private LocalDateTime archivedAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
