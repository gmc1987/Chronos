package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
	java.util.List<WorkflowInstance> findByInitiatorOrderByCreateTimeDesc(String initiator);
}
