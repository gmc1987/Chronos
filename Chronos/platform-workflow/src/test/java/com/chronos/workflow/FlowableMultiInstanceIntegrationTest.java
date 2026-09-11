package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowEdge;
import com.chronos.model.workflow.WorkflowNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableMultiInstanceIntegrationTest {
	private static final String BPMN = """
			<?xml version="1.0" encoding="UTF-8"?>
			<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
			             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			             xmlns:flowable="http://flowable.org/bpmn"
			             targetNamespace="ChronosTest">
			  <process id="dynamicCountersign" isExecutable="true">
			    <startEvent id="start" />
			    <userTask id="prepare" name="前置审批" flowable:assignee="starter" />
			    <userTask id="chronos_rework__prepare" name="发起人修改后重新提交" flowable:assignee="${chronosInitiator}" />
			    <userTask id="countersign" name="多人会签" flowable:assignee="${chronosAssignee}">
			      <multiInstanceLoopCharacteristics isSequential="false"
			          flowable:collection="assignees" flowable:elementVariable="chronosAssignee">
			        <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
			      </multiInstanceLoopCharacteristics>
			    </userTask>
			    <endEvent id="end" />
			    <sequenceFlow id="f1" sourceRef="start" targetRef="prepare" />
			    <sequenceFlow id="f2" sourceRef="prepare" targetRef="countersign" />
			    <sequenceFlow id="f3" sourceRef="countersign" targetRef="end" />
			    <sequenceFlow id="reworkResume" sourceRef="chronos_rework__prepare" targetRef="prepare" />
			  </process>
			</definitions>
			""";

	private ProcessEngine engine;

	@BeforeEach
	void setUp() {
		engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
				.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
				.setAsyncExecutorActivate(false)
				.buildProcessEngine();
		engine.getRepositoryService()
				.createDeployment()
				.addBytes("dynamic-countersign.bpmn20.xml", BPMN.getBytes(StandardCharsets.UTF_8))
				.deploy();
	}

	@AfterEach
	void tearDown() {
		engine.close();
	}

	@Test
	void dynamicAddSignParticipatesInAllApprovalCompletionCondition() {
		var process = engine.getRuntimeService().startProcessInstanceByKey(
				"dynamicCountersign",
				Map.of("assignees", List.of("user-a", "user-b")));
		complete("prepare", "starter");
		assertThat(activeTasks("countersign"))
				.extracting(Task::getAssignee)
				.containsExactlyInAnyOrder("user-a", "user-b");

		engine.getRuntimeService().addMultiInstanceExecution(
				"countersign",
				process.getId(),
				Map.of("chronosAssignee", "user-c"));
		assertThat(activeTasks("countersign"))
				.extracting(Task::getAssignee)
				.containsExactlyInAnyOrder("user-a", "user-b", "user-c");

		complete("countersign", "user-a");
		complete("countersign", "user-b");
		assertThat(engine.getRuntimeService().createProcessInstanceQuery()
				.processInstanceId(process.getId())
				.singleResult()).isNotNull();
		assertThat(activeTasks("countersign"))
				.extracting(Task::getAssignee)
				.containsExactly("user-c");

		complete("countersign", "user-c");
		assertThat(engine.getRuntimeService().createProcessInstanceQuery()
				.processInstanceId(process.getId())
				.singleResult()).isNull();
	}

	@Test
	void returningMultiInstanceNodeCollapsesAllActiveExecutions() {
		var process = engine.getRuntimeService().startProcessInstanceByKey(
				"dynamicCountersign",
				Map.of("assignees", List.of("user-a", "user-b")));
		complete("prepare", "starter");
		List<String> executionIds = activeTasks("countersign").stream()
				.map(Task::getExecutionId)
				.toList();

		engine.getRuntimeService().createChangeActivityStateBuilder()
				.processInstanceId(process.getId())
				.moveExecutionsToSingleActivityId(executionIds, "prepare")
				.changeState();

		assertThat(activeTasks("countersign")).isEmpty();
		assertThat(activeTasks("prepare"))
				.extracting(Task::getAssignee)
				.containsExactly("starter");
	}

	@Test
	void starterReworkTaskReturnsToOriginalApprovalNode() {
		var process = engine.getRuntimeService().startProcessInstanceByKey(
				"dynamicCountersign",
				Map.of(
						"assignees", List.of("user-a", "user-b"),
						"chronosInitiator", "starter"));
		Task approval = activeTasks("prepare").getFirst();

		engine.getRuntimeService().createChangeActivityStateBuilder()
				.processInstanceId(process.getId())
				.moveExecutionToActivityId(approval.getExecutionId(), "chronos_rework__prepare")
				.changeState();

		assertThat(activeTasks("prepare")).isEmpty();
		assertThat(activeTasks("chronos_rework__prepare"))
				.extracting(Task::getAssignee)
				.containsExactly("starter");

		complete("chronos_rework__prepare", "starter");
		assertThat(activeTasks("chronos_rework__prepare")).isEmpty();
		assertThat(activeTasks("prepare"))
				.extracting(Task::getAssignee)
				.containsExactly("starter");
	}

	@Test
	void generatedBpmnContainsExecutableStarterReworkPath() {
		WorkflowDefinition definition = new WorkflowDefinition();
		definition.setFlowCode("generatedRework");
		definition.setFlowName("发起人退回测试");
		definition.setVersion("v1");
		WorkflowNode start = node("start", "开始", "START", "{}");
		WorkflowNode approval = node(
				"approval",
				"审批",
				"APPROVAL",
				"{\"assigneeMode\":\"USER\",\"approvalMode\":\"SINGLE\"}");
		WorkflowNode end = node("end", "结束", "END", "{}");
		new FlowableDeploymentService(engine.getRepositoryService()).deploy(
				definition,
				List.of(start, approval, end),
				List.of(edge("start", "approval"), edge("approval", "end")));

		var process = engine.getRuntimeService().startProcessInstanceByKey(
				"generatedRework_v1",
				Map.of(
						"chronosAssignee_approval", "approver",
						"chronosInitiator", "starter"));
		Task task = engine.getTaskService().createTaskQuery()
				.processInstanceId(process.getId())
				.taskDefinitionKey("approval")
				.singleResult();
		engine.getRuntimeService().createChangeActivityStateBuilder()
				.processInstanceId(process.getId())
				.moveExecutionToActivityId(task.getExecutionId(), "chronos_rework__approval")
				.changeState();

		assertThat(activeTasks("chronos_rework__approval"))
				.extracting(Task::getAssignee)
				.containsExactly("starter");
		complete("chronos_rework__approval", "starter");
		assertThat(activeTasks("approval"))
				.extracting(Task::getAssignee)
				.containsExactly("approver");
	}

	private WorkflowNode node(String key, String name, String type, String properties) {
		WorkflowNode node = new WorkflowNode();
		node.setNodeKey(key);
		node.setNodeName(name);
		node.setNodeType(type);
		node.setPropertiesJson(properties);
		return node;
	}

	private WorkflowEdge edge(String from, String to) {
		WorkflowEdge edge = new WorkflowEdge();
		edge.setFromNodeKey(from);
		edge.setToNodeKey(to);
		return edge;
	}

	private List<Task> activeTasks(String nodeKey) {
		return engine.getTaskService().createTaskQuery()
				.taskDefinitionKey(nodeKey)
				.active()
				.list();
	}

	private void complete(String nodeKey, String assignee) {
		Task task = engine.getTaskService().createTaskQuery()
				.taskDefinitionKey(nodeKey)
				.taskAssignee(assignee)
				.singleResult();
		engine.getTaskService().complete(task.getId());
	}
}
