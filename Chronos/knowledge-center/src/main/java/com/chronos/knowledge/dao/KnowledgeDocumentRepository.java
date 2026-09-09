package com.chronos.knowledge.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.knowledge.model.KnowledgeDocument;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
	List<KnowledgeDocument> findByKnowledgeBaseIdOrderByCreateTimeDesc(String knowledgeBaseId);

	long countByKnowledgeBaseId(String knowledgeBaseId);
}
