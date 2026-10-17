package com.chronos.knowledge.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.knowledge.model.KnowledgeChunk;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {
	void deleteByDocumentId(String documentId);
	List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);
	long countByDocumentId(String documentId);
	long countByDocumentIdAndEmbeddingIndexedTrue(String documentId);

	@Query("""
			select count(chunk) from KnowledgeChunk chunk
			where chunk.documentId in (
				select document.id from KnowledgeDocument document
				where document.knowledgeBaseId = :baseId
			)
			""")
	long countByKnowledgeBaseId(@Param("baseId") String baseId);

	@Query("""
			select count(chunk) from KnowledgeChunk chunk
			where chunk.embeddingIndexed = true
			  and chunk.documentId in (
				select document.id from KnowledgeDocument document
				where document.knowledgeBaseId = :baseId
			)
			""")
	long countIndexedByKnowledgeBaseId(@Param("baseId") String baseId);

	@Query(value = """
			SELECT chunk.*
			FROM kb_document_chunk chunk
			JOIN kb_document document ON document.id = chunk.document_id
			WHERE document.knowledge_base_id = :baseId
			  -- Vector indexing is independent from the relational chunks. During
			  -- indexing or after a vector dead letter, keyword fallback must keep
			  -- serving the already committed source text.
			  AND document.status IN ('READY', 'INDEXING', 'INDEX_FAILED')
			  AND chunk.content ILIKE concat('%', cast(:keyword AS text), '%')
			ORDER BY document.create_time DESC, chunk.chunk_index ASC
			""", nativeQuery = true)
	List<KnowledgeChunk> search(
			@Param("baseId") String baseId,
			@Param("keyword") String keyword,
			Pageable pageable);
}
