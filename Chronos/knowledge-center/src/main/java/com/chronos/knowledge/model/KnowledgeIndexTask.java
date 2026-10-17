package com.chronos.knowledge.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable intent for an external vector-store mutation. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kb_index_task")
public class KnowledgeIndexTask extends BaseEntity {
	@Column(name = "knowledge_base_id", length = 64, nullable = false)
	private String knowledgeBaseId;

	@Column(name = "document_id", length = 64)
	private String documentId;

	@Column(name = "chunk_id", length = 64, nullable = false)
	private String chunkId;

	@Column(name = "operation", length = 16, nullable = false)
	private String operation;

	@Column(name = "content_snapshot", columnDefinition = "text")
	private String contentSnapshot;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "PENDING";

	@Column(name = "attempt_count", nullable = false)
	private Integer attemptCount = 0;

	@Column(name = "next_attempt_at")
	private LocalDateTime nextAttemptAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;
}
