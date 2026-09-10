package com.chronos.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.knowledge.dao.KnowledgeBaseRepository;
import com.chronos.knowledge.dao.KnowledgeChunkRepository;
import com.chronos.knowledge.dao.KnowledgeDocumentRepository;
import com.chronos.knowledge.model.KnowledgeBase;
import com.chronos.knowledge.model.KnowledgeChunk;
import com.chronos.knowledge.model.KnowledgeDocument;
import com.chronos.service.factory.LLMServiceStrategy;
import com.chronos.service.iService.IAuditLogService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTest {
	@Test
	void rebuildReplacesAllChunksAndUpdatesDocumentMetadata() {
		KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
		KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
		KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
		IAuditLogService audit = mock(IAuditLogService.class);
		KnowledgeDocument document = new KnowledgeDocument();
		document.setId("document-1");
		document.setKnowledgeBaseId("base-1");
		document.setContent("第一段\n\n第二段");
		document.setChunkCount(99);
		document.setStatus("FAILED");
		KnowledgeBase base = new KnowledgeBase();
		base.setEnabled(true);
		when(documents.findLockedById("document-1")).thenReturn(Optional.of(document));
		when(bases.findById("base-1")).thenReturn(Optional.of(base));
		when(documents.save(document)).thenReturn(document);

		KnowledgeDocument result = new KnowledgeService(
				bases,
				documents,
				chunks,
				mock(KnowledgeDocumentTextExtractor.class),
				mock(LLMServiceStrategy.class),
				audit).rebuildDocument("document-1", "knowledge.admin");

		assertThat(result.getChunkCount()).isEqualTo(1);
		assertThat(result.getStatus()).isEqualTo("READY");
		verify(chunks).deleteByDocumentId("document-1");
		verify(chunks).flush();
		verify(chunks).save(any(KnowledgeChunk.class));
		verify(audit).log(
				"knowledge.admin",
				"EDUCATION_KNOWLEDGE_DOCUMENT_REBUILD",
				"documentId=document-1, chunkCount=1");
	}
}
