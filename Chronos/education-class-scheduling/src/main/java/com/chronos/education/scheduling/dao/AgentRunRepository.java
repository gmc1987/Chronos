package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.AgentRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
	Optional<AgentRun> findByOwnerUsernameAndClientRequestId(
			String ownerUsername,
			String clientRequestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from AgentRun run where run.id = :id")
	Optional<AgentRun> findLockedById(@Param("id") String id);
}
