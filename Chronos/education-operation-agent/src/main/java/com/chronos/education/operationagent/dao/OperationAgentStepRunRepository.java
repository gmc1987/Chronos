package com.chronos.education.operationagent.dao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.operationagent.model.OperationAgentStepRun;
public interface OperationAgentStepRunRepository extends JpaRepository<OperationAgentStepRun, String> {
	List<OperationAgentStepRun> findByRunIdOrderByStepOrder(String runId);
	Optional<OperationAgentStepRun> findByRunIdAndStepOrder(String runId, Integer stepOrder);
}
