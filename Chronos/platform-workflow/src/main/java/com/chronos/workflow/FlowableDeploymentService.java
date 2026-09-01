package com.chronos.workflow;

import com.chronos.model.workflow.*;
import java.util.*;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

@Service
public class FlowableDeploymentService {
    private final RepositoryService repositoryService;
    public FlowableDeploymentService(RepositoryService repositoryService){this.repositoryService=repositoryService;}

    public DeploymentResult deploy(WorkflowDefinition definition,List<WorkflowNode> nodes,List<WorkflowEdge> edges){
        String key=safe(definition.getFlowCode())+"_"+safe(definition.getVersion());
        BpmnModel model=new BpmnModel();org.flowable.bpmn.model.Process process=new org.flowable.bpmn.model.Process();process.setId(key);process.setName(definition.getFlowName());model.addProcess(process);
        Map<String,FlowNode> elements=new LinkedHashMap<>();
        for(WorkflowNode node:nodes){FlowNode element=element(node);element.setId(node.getNodeKey());element.setName(node.getNodeName());process.addFlowElement(element);elements.put(node.getNodeKey(),element);}
        int index=0;for(WorkflowEdge edge:edges){SequenceFlow sequence=new SequenceFlow(edge.getFromNodeKey(),edge.getToNodeKey());sequence.setId("sequence_"+(++index));if(edge.getConditionExpr()!=null&&!edge.getConditionExpr().isBlank())sequence.setConditionExpression("${"+edge.getConditionExpr().trim()+"}");process.addFlowElement(sequence);}
        Deployment deployment=repositoryService.createDeployment().key(key).name(definition.getFlowName()+" "+definition.getVersion()).addBpmnModel(key+".bpmn20.xml",model).deploy();
        return new DeploymentResult(deployment.getId(),key);
    }
    private FlowNode element(WorkflowNode node){return switch(node.getNodeType()){
        case "START" -> new StartEvent();case "END" -> new EndEvent();case "CONDITION" -> new ExclusiveGateway();
        case "APPROVAL","TASK","CC" -> userTask(node);
        case "SERVICE_TASK","HTTP_TASK","AGENT_TASK","MESSAGE_TASK" -> serviceTask();
        default -> throw new IllegalArgumentException("Flowable不支持节点类型："+node.getNodeType());};}
    private UserTask userTask(WorkflowNode node){UserTask task=new UserTask();task.setAssignee("${chronosAssignee}");return task;}
    private ServiceTask serviceTask(){ServiceTask task=new ServiceTask();task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);task.setImplementation("${chronosNodeDelegate}");return task;}
    private String safe(String value){return value.replaceAll("[^A-Za-z0-9_]","_");}
    public record DeploymentResult(String deploymentId,String processKey) {}
}
