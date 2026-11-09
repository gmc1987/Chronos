package com.chronos.knowledge.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.knowledge.model.KnowledgeBase;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {
	List<KnowledgeBase> findAllByOrderByCreateTimeDesc();
	Page<KnowledgeBase> findAllByOrderByCreateTimeDesc(Pageable pageable);
	List<KnowledgeBase> findByOrganizationIdInOrderByCreateTimeDesc(java.util.Collection<String> organizationIds);
	Page<KnowledgeBase> findByOrganizationIdInOrderByCreateTimeDesc(java.util.Collection<String> organizationIds, Pageable pageable);

	boolean existsByBaseCode(String baseCode);
}
