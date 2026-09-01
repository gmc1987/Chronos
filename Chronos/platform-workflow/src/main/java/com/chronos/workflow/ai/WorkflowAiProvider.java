package com.chronos.workflow.ai;

import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowEdge;
import com.chronos.model.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;

/** Extension point for an on-premise/private AI model. Implementations must return structured data only. */
public interface WorkflowAiProvider {
    String mode();
    default boolean external() { return false; }
    List<AiFinding> review(WorkflowDefinition definition,List<WorkflowNode> nodes,List<WorkflowEdge> edges);
    Map<String,Object> draft(String requirement);
    record AiFinding(String nodeId,String severity,String category,String title,String description,String suggestion) {}
}
