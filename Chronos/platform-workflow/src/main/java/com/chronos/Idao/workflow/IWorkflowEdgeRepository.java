package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowEdge;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowEdgeRepository extends JpaRepository<WorkflowEdge, String> {
	List<WorkflowEdge> findByFlowIdOrderByCreateTimeAsc(String flowId);

	List<WorkflowEdge> findByFlowIdAndFromNodeKey(String flowId, String fromNodeKey);

	void deleteByFlowId(String flowId);
}
