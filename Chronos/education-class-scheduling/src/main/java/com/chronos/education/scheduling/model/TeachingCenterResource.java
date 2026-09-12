package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 教学中心统一资源。资源类型区分计划、教案、备课、课件、材料、题库、错题和教研，
 * 但全部遵守同一套 offering 数据权限和文件引用规则，避免重复建设作业提交模型。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teaching_center_resource", indexes = {
		@Index(name = "idx_teaching_resource_type_offering", columnList = "resource_type,offering_id"),
		@Index(name = "idx_teaching_resource_status", columnList = "status") })
public class TeachingCenterResource extends BaseEntity {
	@Column(name = "resource_type", nullable = false, length = 32)
	private String resourceType;
	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
	@Column(name = "schedule_entry_id", length = 64)
	private String scheduleEntryId;
	@Column(name = "title", nullable = false, length = 200)
	private String title;
	@Column(name = "category", length = 64)
	private String category;
	@Column(name = "version_no", nullable = false)
	private Integer versionNo = 1;
	@Column(name = "status", nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(name = "content", columnDefinition = "text")
	private String content;
	@Column(name = "file_id", length = 64)
	private String fileId;
	@Column(name = "metadata_json", columnDefinition = "text")
	private String metadataJson;
	@Column(name = "archived", nullable = false)
	private boolean archived = false;
}
