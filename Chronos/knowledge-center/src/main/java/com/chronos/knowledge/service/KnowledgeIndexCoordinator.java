package com.chronos.knowledge.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.knowledge.dao.KnowledgeBaseRepository;
import com.chronos.knowledge.dao.KnowledgeChunkRepository;
import com.chronos.knowledge.dao.KnowledgeDocumentRepository;
import com.chronos.knowledge.dao.KnowledgeIndexTaskRepository;
import com.chronos.knowledge.model.KnowledgeBase;
import com.chronos.knowledge.model.KnowledgeChunk;
import com.chronos.knowledge.model.KnowledgeDocument;
import com.chronos.knowledge.model.KnowledgeIndexTask;

/** Creates durable vector-store work inside the same transaction as the source data. */
@Service
public class KnowledgeIndexCoordinator {
	private final KnowledgeBaseRepository bases;
	private final KnowledgeDocumentRepository documents;
	private final KnowledgeChunkRepository chunks;
	private final KnowledgeIndexTaskRepository tasks;

	public KnowledgeIndexCoordinator(
			KnowledgeBaseRepository bases,
			KnowledgeDocumentRepository documents,
			KnowledgeChunkRepository chunks,
			KnowledgeIndexTaskRepository tasks) {
		this.bases = bases;
		this.documents = documents;
		this.chunks = chunks;
		this.tasks = tasks;
	}

	public boolean vectorEnabled(String knowledgeBaseId) {
		return bases.findById(knowledgeBaseId)
				.map(base -> "VECTOR".equalsIgnoreCase(base.getRetrievalMode()))
				.orElse(false);
	}

	public void enqueueUpsert(String knowledgeBaseId, String documentId, KnowledgeChunk chunk) {
		if (!vectorEnabled(knowledgeBaseId)) {
			return;
		}
		KnowledgeIndexTask task = new KnowledgeIndexTask();
		task.setKnowledgeBaseId(knowledgeBaseId);
		task.setDocumentId(documentId);
		task.setChunkId(chunk.getId());
		task.setOperation("UPSERT");
		task.setContentSnapshot(chunk.getContent());
		task.setStatus("PENDING");
		task.setNextAttemptAt(LocalDateTime.now());
		tasks.save(task);
		markIndexing(knowledgeBaseId, documentId);
	}

	public void enqueueDeletes(String knowledgeBaseId, String documentId, List<KnowledgeChunk> oldChunks) {
		boolean queued = false;
		for (KnowledgeChunk chunk : oldChunks) {
			if (!Boolean.TRUE.equals(chunk.getEmbeddingIndexed()) && !vectorEnabled(knowledgeBaseId)) {
				continue;
			}
			KnowledgeIndexTask task = new KnowledgeIndexTask();
			task.setKnowledgeBaseId(knowledgeBaseId);
			task.setDocumentId(documentId);
			task.setChunkId(chunk.getId());
			task.setOperation("DELETE");
			task.setStatus("PENDING");
			task.setNextAttemptAt(LocalDateTime.now());
			tasks.save(task);
			queued = true;
		}
		if (queued) {
			markIndexing(knowledgeBaseId, documentId);
		}
	}

	private void markIndexing(String knowledgeBaseId, String documentId) {
		bases.findById(knowledgeBaseId).ifPresent(base -> {
			base.setIndexStatus("INDEXING");
			base.setIndexError(null);
			bases.save(base);
		});
		if (documentId != null) {
			documents.findById(documentId).ifPresent(document -> {
				document.setStatus("INDEXING");
				documents.save(document);
			});
		}
	}

	@Transactional(readOnly = true)
	public Page<KnowledgeIndexTask> tasks(String knowledgeBaseId, int page, int size) {
		bases.findById(knowledgeBaseId)
				.orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
		return tasks.findByKnowledgeBaseIdOrderByCreateTimeDesc(
				knowledgeBaseId,
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
	}

	@Transactional
	public KnowledgeIndexTask retry(String id) {
		KnowledgeIndexTask task = tasks.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("索引任务不存在"));
		if (!List.of("DEAD", "RETRY").contains(task.getStatus())) {
			throw new IllegalStateException("只有失败或待重试任务可以重新执行");
		}
		task.setStatus("PENDING");
		task.setAttemptCount(0);
		task.setLastError(null);
		task.setNextAttemptAt(LocalDateTime.now());
		markIndexing(task.getKnowledgeBaseId(), task.getDocumentId());
		return tasks.save(task);
	}

	@Transactional(readOnly = true)
	public IndexConsistency consistency(String knowledgeBaseId) {
		KnowledgeBase base = bases.findById(knowledgeBaseId)
				.orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
		long chunkCount = chunks.countByKnowledgeBaseId(knowledgeBaseId);
		long indexedCount = chunks.countIndexedByKnowledgeBaseId(knowledgeBaseId);
		long pendingCount = tasks.countByKnowledgeBaseIdAndStatusIn(
				knowledgeBaseId,
				List.of("PENDING", "PROCESSING", "RETRY"));
		long deadCount = tasks.countByKnowledgeBaseIdAndStatusIn(knowledgeBaseId, List.of("DEAD"));
		boolean vector = "VECTOR".equalsIgnoreCase(base.getRetrievalMode());
		return new IndexConsistency(
				knowledgeBaseId,
				base.getIndexStatus(),
				chunkCount,
				indexedCount,
				pendingCount,
				deadCount,
				!vector || (chunkCount == indexedCount && pendingCount == 0 && deadCount == 0),
				base.getIndexError());
	}

	public record IndexConsistency(
			String knowledgeBaseId,
			String indexStatus,
			long chunkCount,
			long indexedCount,
			long pendingCount,
			long deadCount,
			boolean consistent,
			String lastError) {
	}
}
