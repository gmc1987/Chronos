package com.chronos.knowledge.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.knowledge.model.KnowledgeChunk;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {
	void deleteByDocumentId(String documentId);

	@Query(value = """
			SELECT chunk.*
			FROM kb_document_chunk chunk
			JOIN kb_document document ON document.id = chunk.document_id
			WHERE document.knowledge_base_id = :baseId
			  AND document.status = 'READY'
			  AND chunk.content ILIKE concat('%', cast(:keyword AS text), '%')
			ORDER BY document.create_time DESC, chunk.chunk_index ASC
			""", nativeQuery = true)
	List<KnowledgeChunk> search(
			@Param("baseId") String baseId,
			@Param("keyword") String keyword,
			Pageable pageable);
}
