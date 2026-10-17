package com.chronos.knowledge.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.ai.service.EmbeddingService;
import com.chronos.knowledge.dao.KnowledgeBaseRepository;
import com.chronos.knowledge.dao.KnowledgeChunkRepository;
import com.chronos.knowledge.dao.KnowledgeDocumentRepository;
import com.chronos.knowledge.dao.KnowledgeIndexTaskRepository;
import com.chronos.knowledge.model.KnowledgeBase;
import com.chronos.knowledge.model.KnowledgeIndexTask;

/** Executes one durable index mutation at a time and records retry state. */
@Service
public class KnowledgeIndexTaskProcessor {
	private final KnowledgeIndexTaskRepository tasks;
	private final KnowledgeBaseRepository bases;
	private final KnowledgeDocumentRepository documents;
	private final KnowledgeChunkRepository chunks;
	private final EmbeddingService embeddings;
	private final MilvusVectorStore vectors;
	private final int maxAttempts;

	public KnowledgeIndexTaskProcessor(
			KnowledgeIndexTaskRepository tasks,
			KnowledgeBaseRepository bases,
			KnowledgeDocumentRepository documents,
			KnowledgeChunkRepository chunks,
			EmbeddingService embeddings,
			MilvusVectorStore vectors,
			@Value("${chronos.knowledge.index.max-attempts:5}") int maxAttempts) {
		this.tasks = tasks;
		this.bases = bases;
		this.documents = documents;
		this.chunks = chunks;
		this.embeddings = embeddings;
		this.vectors = vectors;
		this.maxAttempts = Math.max(1, maxAttempts);
	}

	@Transactional(readOnly = true)
	public List<String> dueIds(int batchSize) {
		return tasks.findDueIds(
				LocalDateTime.now(),
				PageRequest.of(0, Math.min(Math.max(batchSize, 1), 100)));
	}

	@Transactional
	public void process(String id) {
		KnowledgeIndexTask task = tasks.findLockedById(id).orElse(null);
		if (task == null || !List.of("PENDING", "RETRY").contains(task.getStatus())) {
			return;
		}
		task.setStatus("PROCESSING");
		tasks.save(task);
		try {
			if (!vectors.available()) {
				throw new IllegalStateException("Milvus endpoint 未配置");
			}
			if ("DELETE".equals(task.getOperation())) {
				vectors.delete(task.getKnowledgeBaseId(), task.getChunkId());
			} else {
				KnowledgeBase base = bases.findById(task.getKnowledgeBaseId())
						.orElseThrow(() -> new IllegalStateException("知识库不存在"));
				float[] vector = embeddings.embed(task.getContentSnapshot(), base.getEmbeddingModelId());
				vectors.upsert(task.getKnowledgeBaseId(), task.getChunkId(), vector);
				chunks.findById(task.getChunkId()).ifPresent(chunk -> {
					chunk.setEmbeddingIndexed(true);
					chunks.save(chunk);
				});
			}
			task.setStatus("SUCCEEDED");
			task.setLastError(null);
			task.setNextAttemptAt(null);
			tasks.save(task);
			refreshStatus(task.getKnowledgeBaseId(), task.getDocumentId());
		} catch (RuntimeException exception) {
			int attempts = task.getAttemptCount() + 1;
			task.setAttemptCount(attempts);
			task.setLastError(abbreviate(exception.getMessage()));
			if (attempts >= maxAttempts) {
				task.setStatus("DEAD");
				task.setNextAttemptAt(null);
			} else {
				task.setStatus("RETRY");
				task.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << attempts)));
			}
			tasks.save(task);
			markFailure(task, attempts >= maxAttempts);
		}
	}

	private void refreshStatus(String baseId, String documentId) {
		long pending = tasks.countByKnowledgeBaseIdAndStatusIn(
				baseId,
				List.of("PENDING", "PROCESSING", "RETRY"));
		long dead = tasks.countByKnowledgeBaseIdAndStatusIn(baseId, List.of("DEAD"));
		KnowledgeBase base = bases.findById(baseId).orElse(null);
		if (base != null) {
			base.setIndexStatus(dead > 0 ? fallbackStatus(base) : pending > 0 ? "INDEXING" : "READY");
			if (dead == 0) {
				base.setIndexError(null);
			}
			bases.save(base);
		}
		if (documentId != null) {
			long documentPending = tasks.countByDocumentIdAndStatusIn(
					documentId,
					List.of("PENDING", "PROCESSING", "RETRY"));
			long documentDead = tasks.countByDocumentIdAndStatusIn(documentId, List.of("DEAD"));
			if (documentPending == 0 && documentDead == 0) {
				documents.findById(documentId).ifPresent(document -> {
					document.setStatus("READY");
					documents.save(document);
				});
			}
		}
	}

	private void markFailure(KnowledgeIndexTask task, boolean dead) {
		bases.findById(task.getKnowledgeBaseId()).ifPresent(base -> {
			base.setIndexStatus(dead ? fallbackStatus(base) : "INDEXING");
			base.setIndexError(task.getLastError());
			bases.save(base);
		});
		if (dead && task.getDocumentId() != null) {
			documents.findById(task.getDocumentId()).ifPresent(document -> {
				document.setStatus("INDEX_FAILED");
				documents.save(document);
			});
		}
	}

	private String fallbackStatus(KnowledgeBase base) {
		return Boolean.TRUE.equals(base.getAllowKeywordFallback()) ? "FALLBACK" : "FAILED";
	}

	private String abbreviate(String message) {
		String value = message == null || message.isBlank() ? "索引任务执行失败" : message;
		return value.length() <= 2000 ? value : value.substring(0, 2000);
	}
}
