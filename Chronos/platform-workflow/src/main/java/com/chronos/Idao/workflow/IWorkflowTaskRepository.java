package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowTask;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowTaskRepository extends JpaRepository<WorkflowTask, String> {
	List<WorkflowTask> findByAssigneeAndStatusOrderByCreateTimeDesc(String assignee, String status);
	List<WorkflowTask> findByAssigneeOrderByCreateTimeDesc(String assignee);

	List<WorkflowTask> findByInstanceIdOrderByCreateTimeAsc(String instanceId);
	List<WorkflowTask> findByStatusAndDueAtBefore(String status,java.time.LocalDateTime dueAt);
}
