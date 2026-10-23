package com.chronos.education.operationagent.dao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.chronos.education.operationagent.model.OperationAgentRun;
public interface OperationAgentRunRepository extends JpaRepository<OperationAgentRun, String> {
	List<OperationAgentRun> findTop30ByOwnerUsernameOrderByCreateTimeDesc(String ownerUsername);
	List<OperationAgentRun> findTop30ByOrderByCreateTimeDesc();
	boolean existsByPlanIdAndStatusIn(String planId, java.util.Collection<String> statuses);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select value from OperationAgentRun value where value.id=:id")
	Optional<OperationAgentRun> findLockedById(@Param("id") String id);
}
