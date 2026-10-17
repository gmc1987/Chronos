package com.chronos.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.ai.service.EmbeddingService;
import com.chronos.knowledge.dao.KnowledgeBaseRepository;
import com.chronos.knowledge.dao.KnowledgeChunkRepository;
import com.chronos.knowledge.dao.KnowledgeDocumentRepository;
import com.chronos.knowledge.dao.KnowledgeIndexTaskRepository;
import com.chronos.knowledge.model.KnowledgeBase;
import com.chronos.knowledge.model.KnowledgeChunk;
import com.chronos.knowledge.model.KnowledgeIndexTask;

class KnowledgeIndexTaskProcessorTest {
	@Test
	void successfulUpsertMarksChunkAndTaskOnlyAfterVectorWrite() {
		KnowledgeIndexTaskRepository tasks = mock(KnowledgeIndexTaskRepository.class);
		KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
		KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
		KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
		EmbeddingService embeddings = mock(EmbeddingService.class);
		MilvusVectorStore vectors = mock(MilvusVectorStore.class);
		KnowledgeIndexTask task = new KnowledgeIndexTask();
		task.setId("task-1");
		task.setKnowledgeBaseId("base-1");
		task.setDocumentId("document-1");
		task.setChunkId("chunk-1");
		task.setOperation("UPSERT");
		task.setContentSnapshot("制度内容");
		task.setStatus("PENDING");
		KnowledgeBase base = new KnowledgeBase();
		base.setId("base-1");
		base.setEmbeddingModelId("embedding-1");
		KnowledgeChunk chunk = new KnowledgeChunk();
		chunk.setId("chunk-1");
		chunk.setEmbeddingIndexed(false);
		when(tasks.findLockedById("task-1")).thenReturn(Optional.of(task));
		when(vectors.available()).thenReturn(true);
		when(bases.findById("base-1")).thenReturn(Optional.of(base));
		float[] vector = new float[] { 1F, 2F };
		when(embeddings.embed("制度内容", "embedding-1")).thenReturn(vector);
		when(chunks.findById("chunk-1")).thenReturn(Optional.of(chunk));

		new KnowledgeIndexTaskProcessor(
				tasks, bases, documents, chunks, embeddings, vectors, 3).process("task-1");

		verify(vectors).upsert("base-1", "chunk-1", vector);
		verify(chunks).save(chunk);
		verify(tasks, org.mockito.Mockito.atLeastOnce()).save(any(KnowledgeIndexTask.class));
		assertThat(chunk.getEmbeddingIndexed()).isTrue();
		assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
		assertThat(task.getLastError()).isNull();
	}

	@Test
	void unavailableVectorStoreCreatesDeadLetterAndFallbackStatus() {
		KnowledgeIndexTaskRepository tasks = mock(KnowledgeIndexTaskRepository.class);
		KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
		KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
		KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
		EmbeddingService embeddings = mock(EmbeddingService.class);
		MilvusVectorStore vectors = mock(MilvusVectorStore.class);
		KnowledgeIndexTask task = new KnowledgeIndexTask();
		task.setId("task-1");
		task.setKnowledgeBaseId("base-1");
		task.setDocumentId("document-1");
		task.setChunkId("chunk-1");
		task.setOperation("UPSERT");
		task.setStatus("PENDING");
		KnowledgeBase base = new KnowledgeBase();
		base.setId("base-1");
		base.setAllowKeywordFallback(true);
		when(tasks.findLockedById("task-1")).thenReturn(Optional.of(task));
		when(vectors.available()).thenReturn(false);
		when(bases.findById("base-1")).thenReturn(Optional.of(base));

		new KnowledgeIndexTaskProcessor(
				tasks, bases, documents, chunks, embeddings, vectors, 1).process("task-1");

		assertThat(task.getStatus()).isEqualTo("DEAD");
		assertThat(task.getLastError()).contains("Milvus endpoint");
		assertThat(base.getIndexStatus()).isEqualTo("FALLBACK");
		assertThat(base.getIndexError()).contains("Milvus endpoint");
	}
}
