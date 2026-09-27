package com.chronos.integration.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.integration.model.SyncCursor;

public interface SyncCursorRepository extends JpaRepository<SyncCursor, String> {
	Optional<SyncCursor> findByJobId(String jobId);
}
