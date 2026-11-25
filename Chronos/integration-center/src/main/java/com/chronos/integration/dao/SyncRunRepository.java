package com.chronos.integration.dao;

import com.chronos.integration.model.SyncRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, String> {
	List<SyncRun> findByJobIdOrderByStartedAtDesc(String jobId);
	Optional<SyncRun> findFirstByJobIdOrderByStartedAtDesc(String jobId);
}
