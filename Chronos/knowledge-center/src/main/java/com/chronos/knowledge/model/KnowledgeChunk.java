package com.chronos.knowledge.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文档的稳定分段单元，用于检索和来源定位。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "kb_document_chunk",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_kb_chunk_document_index",
				columnNames = { "document_id", "chunk_index" }))
public class KnowledgeChunk extends BaseEntity {
	@Column(name = "document_id", length = 64, nullable = false)
	private String documentId;

	@Column(name = "chunk_index", nullable = false)
	private Integer chunkIndex;

	@Column(name = "content", columnDefinition = "text", nullable = false)
	private String content;

	@Column(name = "embedding_indexed", nullable = false)
	private Boolean embeddingIndexed = false;
}
