package com.chronos.knowledge.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识文档保留原始来源，检索结果才能形成可审计引用。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kb_document")
public class KnowledgeDocument extends BaseEntity {
	@Column(name = "knowledge_base_id", length = 64, nullable = false)
	private String knowledgeBaseId;

	@Column(name = "title", length = 256, nullable = false)
	private String title;

	@Column(name = "source_type", length = 32, nullable = false)
	private String sourceType;

	@Column(name = "original_filename", length = 512)
	private String originalFilename;

	@Column(name = "content", columnDefinition = "text", nullable = false)
	private String content;

	@Column(name = "chunk_count", nullable = false)
	private Integer chunkCount = 0;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "READY";
}
