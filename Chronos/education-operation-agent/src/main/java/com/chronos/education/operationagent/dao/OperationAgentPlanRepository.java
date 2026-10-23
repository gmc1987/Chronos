package com.chronos.education.operationagent.dao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.chronos.education.operationagent.model.OperationAgentPlan;
public interface OperationAgentPlanRepository extends JpaRepository<OperationAgentPlan, String> {
	List<OperationAgentPlan> findAllByOrderByCreateTimeDesc();
	List<OperationAgentPlan> findByEnabledTrueAndCycleTypeAndNextScheduledAtLessThanEqual(
			String cycleType, java.time.LocalDateTime currentTime);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select value from OperationAgentPlan value where value.id=:id")
	java.util.Optional<OperationAgentPlan> findLockedById(@Param("id") String id);
}
