package com.chronos.message.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 通知与公告共享的发布主体，类型差异由 publicationType 表达。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "msg_publication", comment = "通知公告发布主体")
public class Publication extends BaseEntity {
	@Version
	@Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
	private Long lockVersion = 0L;

	@Column(name = "publication_type", length = 32, nullable = false)
	private String publicationType;

	@Column(name = "content_type", length = 32, nullable = false)
	private String contentType = "RICH_TEXT";

	@Column(name = "title", length = 300, nullable = false)
	private String title;

	@Column(name = "summary", length = 1000)
	private String summary;

	@Column(name = "content", columnDefinition = "text")
	private String content;

	@Column(name = "status", length = 32, nullable = false)
	private String status = "DRAFT";

	@Column(name = "importance", length = 16, nullable = false)
	private String importance = "NORMAL";

	@Column(name = "pinned", nullable = false)
	private Boolean pinned = false;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;

	@Column(name = "must_read", nullable = false)
	private Boolean mustRead = false;

	@Column(
			name = "audience_mode",
			length = 16,
			nullable = false,
			columnDefinition = "varchar(16) default 'SNAPSHOT'")
	private String audienceMode = "SNAPSHOT";

	@Column(name = "owner_organization_id", length = 64)
	private String ownerOrganizationId;

	@Column(name = "read_deadline")
	private LocalDateTime readDeadline;

	@Column(name = "approval_required", nullable = false, columnDefinition = "boolean default false")
	private Boolean approvalRequired = false;

	@Column(name = "approval_status", length = 32)
	private String approvalStatus;

	@Column(name = "approval_workflow_definition_id", length = 64)
	private String approvalWorkflowDefinitionId;

	@Column(name = "approval_instance_id", length = 64)
	private String approvalInstanceId;

	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;

	@Column(name = "submitted_by", length = 128)
	private String submittedBy;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "reviewed_by", length = 128)
	private String reviewedBy;

	@Column(name = "review_comment", length = 1000)
	private String reviewComment;

	@Column(name = "publish_at")
	private LocalDateTime publishAt;

	@Column(name = "expire_at")
	private LocalDateTime expireAt;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;

	@Column(name = "withdrawn_by", length = 128)
	private String withdrawnBy;

	@Column(name = "version_no", nullable = false)
	private Integer versionNo = 1;

	@Column(name = "archived", nullable = false, columnDefinition = "boolean default false")
	private Boolean archived = false;

	@Column(name = "archived_at")
	private LocalDateTime archivedAt;

	@Column(name = "archived_by", length = 128)
	private String archivedBy;
}
