package com.chronos.education.operationagent.dao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.operationagent.model.OperationAgentPlanStep;
public interface OperationAgentPlanStepRepository extends JpaRepository<OperationAgentPlanStep, String> {
	List<OperationAgentPlanStep> findByPlanIdOrderByStepOrder(String planId);
	void deleteByPlanId(String planId);
}
