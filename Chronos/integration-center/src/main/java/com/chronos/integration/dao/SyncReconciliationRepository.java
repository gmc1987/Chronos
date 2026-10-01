package com.chronos.integration.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.integration.model.SyncReconciliation;

public interface SyncReconciliationRepository extends JpaRepository<SyncReconciliation, String> {
	List<SyncReconciliation> findByRunIdOrderByCreateTimeDesc(String runId);
}
