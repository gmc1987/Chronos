package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;

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
