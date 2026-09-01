package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IWorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, String> {
	boolean existsByFlowCodeAndVersion(String flowCode, String version);
	List<WorkflowDefinition> findByStatusOrderByFlowNameAsc(String status);
}
