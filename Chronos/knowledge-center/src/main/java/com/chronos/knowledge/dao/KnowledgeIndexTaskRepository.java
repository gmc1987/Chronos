package com.chronos.knowledge.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.knowledge.model.KnowledgeIndexTask;

import jakarta.persistence.LockModeType;

public interface KnowledgeIndexTaskRepository extends JpaRepository<KnowledgeIndexTask, String> {
	Page<KnowledgeIndexTask> findByKnowledgeBaseIdOrderByCreateTimeDesc(String knowledgeBaseId, Pageable pageable);

	long countByKnowledgeBaseIdAndStatusIn(String knowledgeBaseId, List<String> statuses);

	long countByDocumentIdAndStatusIn(String documentId, List<String> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select task from KnowledgeIndexTask task where task.id = :id")
	java.util.Optional<KnowledgeIndexTask> findLockedById(@Param("id") String id);

	@Query("""
			select task.id from KnowledgeIndexTask task
			where task.status in ('PENDING', 'RETRY')
			  and (task.nextAttemptAt is null or task.nextAttemptAt <= :now)
			order by task.createTime
			""")
	List<String> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);
}
