package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowExecutionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowExecutionLogRepository extends JpaRepository<WorkflowExecutionLog, String> {
	List<WorkflowExecutionLog> findTop100ByInstanceIdOrderByCreateTimeDesc(String instanceId);

	List<WorkflowExecutionLog> findTop100ByOrderByCreateTimeDesc();
}
