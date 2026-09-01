package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowNode;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowNodeRepository extends JpaRepository<WorkflowNode, String> {
	List<WorkflowNode> findByFlowIdOrderByCreateTimeAsc(String flowId);

	Optional<WorkflowNode> findByFlowIdAndNodeKey(String flowId, String nodeKey);

	void deleteByFlowId(String flowId);
}
