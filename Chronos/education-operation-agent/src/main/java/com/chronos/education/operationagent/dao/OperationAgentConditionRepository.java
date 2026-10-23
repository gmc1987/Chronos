package com.chronos.education.operationagent.dao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.operationagent.model.OperationAgentCondition;
public interface OperationAgentConditionRepository extends JpaRepository<OperationAgentCondition, String> {
	List<OperationAgentCondition> findByRunIdOrderByConditionVersion(String runId);
}
