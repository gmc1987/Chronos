package com.chronos.workflow;

import com.chronos.model.workflow.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

@Service
public class FlowableDeploymentService {
	private static final String REWORK_PREFIX = "chronos_rework__";

	private final RepositoryService repositoryService;
	private final ObjectMapper json = new ObjectMapper();

	public FlowableDeploymentService(RepositoryService repositoryService) {
		this.repositoryService = repositoryService;
	}

	public DeploymentResult deploy(WorkflowDefinition definition, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
		String key = safe(definition.getFlowCode()) + "_" + safe(definition.getVersion());
		BpmnModel model = new BpmnModel();
		org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
		process.setId(key);
		process.setName(definition.getFlowName());
		model.addProcess(process);
		Map<String, FlowNode> elements = new LinkedHashMap<>();
		for (WorkflowNode node : nodes) {
			FlowNode element = element(node);
			element.setId(node.getNodeKey());
			element.setName(node.getNodeName());
			process.addFlowElement(element);
			elements.put(node.getNodeKey(), element);
		}
		// 为每个人工节点生成一个正常路径不可达的发起人修改任务。退回时通过 Flowable
		// ChangeActivityState 动态进入，提交后沿唯一出口回到原人工节点。
		for (WorkflowNode node : nodes) {
			if (!Set.of("APPROVAL", "TASK").contains(node.getNodeType())) {
				continue;
			}
			UserTask rework = new UserTask();
			rework.setId(reworkKey(node.getNodeKey()));
			rework.setName("发起人修改后重新提交");
			rework.setAssignee("${chronosInitiator}");
			process.addFlowElement(rework);
			SequenceFlow resume = new SequenceFlow(rework.getId(), node.getNodeKey());
			resume.setId("sequence_rework_" + safe(node.getNodeKey()));
			process.addFlowElement(resume);
		}
		int index = 0;
		for (WorkflowEdge edge : edges) {
			SequenceFlow sequence = new SequenceFlow(edge.getFromNodeKey(), edge.getToNodeKey());
			sequence.setId("sequence_" + (++index));
			if (edge.getConditionExpr() != null && !edge.getConditionExpr().isBlank())
				sequence.setConditionExpression("${" + edge.getConditionExpr().trim() + "}");
			process.addFlowElement(sequence);
			if (Boolean.TRUE.equals(edge.getIsDefault())
					&& elements.get(edge.getFromNodeKey()) instanceof ExclusiveGateway gateway) {
				// Flowable 的默认分支必须写入 BPMN gateway.default，空条件并不等同于默认分支。
				gateway.setDefaultFlow(sequence.getId());
			}
		}
		Deployment deployment = repositoryService.createDeployment().key(key)
				.name(definition.getFlowName() + " " + definition.getVersion()).addBpmnModel(key + ".bpmn20.xml", model)
				.deploy();
		return new DeploymentResult(deployment.getId(), key);
	}

	private FlowNode element(WorkflowNode node) {
		return switch (node.getNodeType()) {
		case "START" -> new StartEvent();
		case "END" -> new EndEvent();
		case "CONDITION", "EXCLUSIVE_GATEWAY" -> new ExclusiveGateway();
		case "PARALLEL_GATEWAY" -> new ParallelGateway();
		case "INCLUSIVE_GATEWAY" -> new InclusiveGateway();
		case "TIMER" -> timerEvent(node);
		case "SUB_PROCESS" -> callActivity(node);
		case "APPROVAL", "TASK", "CC" -> userTask(node);
		case "SERVICE_TASK", "HTTP_TASK", "AGENT_TASK", "MESSAGE_TASK" -> serviceTask(node);
		default -> throw new IllegalArgumentException("Flowable不支持节点类型：" + node.getNodeType());
		};
	}

	private UserTask userTask(WorkflowNode node) {
		UserTask task = new UserTask();
		JsonNode properties = properties(node);
		String approvalMode = properties.path("approvalMode").asText("SINGLE");
		boolean claimRequired = properties.path("claimRequired").asBoolean(false);

		// 候选人解析仍由 Chronos 的组织、岗位和流程权限模型负责，Flowable 只消费解析结果。
		// 这样角色编码无需重复同步到 Flowable Identity 表，也避免出现无人认领的悬空任务。
		if ("SINGLE".equals(approvalMode)
				&& !claimRequired
				&& !"ROLE".equals(properties.path("assigneeMode").asText())) {
			task.setAssignee("${chronosAssignee_" + safe(node.getNodeKey()) + "}");
		} else if (!"SINGLE".equals(approvalMode)) {
			configureMultiInstance(task, node, properties, approvalMode);
		}

		int dueHours = properties.path("dueHours").asInt(0);
		if (dueHours > 0) {
			// timeoutSec 是执行器超时；人工任务 SLA 使用 dueHours，两者不能混用。
			task.setDueDate("PT" + dueHours + "H");
		}
		return task;
	}

	private ServiceTask serviceTask(WorkflowNode node) {
		ServiceTask task = new ServiceTask();
		task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
		task.setImplementation("${chronosNodeDelegate}");
		// 自动节点交给 Flowable Job Executor 异步执行，接口超时不会占用请求线程。
		task.setAsynchronous(true);

		int retryCount = Math.max(1, Objects.requireNonNullElse(node.getRetryMax(), 0) + 1);
		int retryInterval = Math.max(1, Objects.requireNonNullElse(node.getRetryIntervalSec(), 60));
		// Flowable 的次数包含首次执行，因此 R1 表示失败后直接进入死信；R4 表示最多重试三次。
		// 由引擎持有重试计数和调度时间，应用重启或集群切换时不会丢失重试进度。
		task.setFailedJobRetryTimeCycleValue("R" + retryCount + "/PT" + retryInterval + "S");
		return task;
	}

	private void configureMultiInstance(UserTask task, WorkflowNode node, JsonNode properties, String approvalMode) {
		MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
		loop.setInputDataItem("${chronosAssignees_" + safe(node.getNodeKey()) + "}");
		loop.setElementVariable("chronosAssignee");
		loop.setSequential("SEQUENTIAL".equals(approvalMode));
		task.setAssignee("${chronosAssignee}");

		String condition = switch (approvalMode) {
		case "ANY" -> "${nrOfCompletedInstances >= 1}";
		case "COUNT" -> "${nrOfCompletedInstances >= " + Math.max(1, properties.path("approvalCount").asInt(1)) + "}";
		case "PERCENTAGE" -> {
			int percentage = Math.min(100, Math.max(1, properties.path("approvalPercentage").asInt(100)));
			yield "${nrOfCompletedInstances * 100 >= nrOfInstances * " + percentage + "}";
		}
		default -> "${nrOfCompletedInstances == nrOfInstances}";
		};
		loop.setCompletionCondition(condition);
		task.setLoopCharacteristics(loop);
	}

	private IntermediateCatchEvent timerEvent(WorkflowNode node) {
		IntermediateCatchEvent event = new IntermediateCatchEvent();
		TimerEventDefinition timer = new TimerEventDefinition();
		String duration = properties(node).path("timerDuration").asText("PT1H");
		timer.setTimeDuration(duration);
		event.addEventDefinition(timer);
		return event;
	}

	private CallActivity callActivity(WorkflowNode node) {
		String calledElement = properties(node).path("calledElement").asText();
		if (calledElement.isBlank()) {
			throw new IllegalArgumentException("子流程节点必须配置 calledElement：" + node.getNodeName());
		}
		CallActivity activity = new CallActivity();
		activity.setCalledElement(calledElement);
		return activity;
	}

	private JsonNode properties(WorkflowNode node) {
		try {
			return json.readTree(node.getPropertiesJson() == null ? "{}" : node.getPropertiesJson());
		} catch (Exception exception) {
			throw new IllegalArgumentException("节点扩展配置不是合法 JSON：" + node.getNodeName(), exception);
		}
	}

	private String safe(String value) {
		return value.replaceAll("[^A-Za-z0-9_]", "_");
	}

	private String reworkKey(String nodeKey) {
		return REWORK_PREFIX + nodeKey;
	}

	public record DeploymentResult(String deploymentId, String processKey) {
	}
}
