package com.chronos.knowledge.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.chronos.knowledge.model.KnowledgeDocument;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
	List<KnowledgeDocument> findByKnowledgeBaseIdOrderByCreateTimeDesc(String knowledgeBaseId);
	Page<KnowledgeDocument> findByKnowledgeBaseIdOrderByCreateTimeDesc(String knowledgeBaseId, Pageable pageable);

	long countByKnowledgeBaseId(String knowledgeBaseId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select document from KnowledgeDocument document where document.id = :id")
	java.util.Optional<KnowledgeDocument> findLockedById(@Param("id") String id);
}
