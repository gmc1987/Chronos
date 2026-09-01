package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowReview;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowReviewRepository extends JpaRepository<WorkflowReview, String> {
	List<WorkflowReview> findByFlowIdOrderByCreateTimeAsc(String flowId);

	void deleteByFlowId(String flowId);
}
