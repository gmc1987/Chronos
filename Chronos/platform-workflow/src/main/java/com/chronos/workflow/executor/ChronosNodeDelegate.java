package com.chronos.workflow.executor;

import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("chronosNodeDelegate")
public class ChronosNodeDelegate implements JavaDelegate {
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final IWorkflowInstanceRepository instances;
	private final IWorkflowNodeRepository nodes;
	private final WorkflowExecutorRegistry executors;
	private final WorkflowExecutionAuditService executionAudit;
	private final ObjectMapper json = new ObjectMapper();

	public ChronosNodeDelegate(
			IWorkflowInstanceRepository instances,
			IWorkflowNodeRepository nodes,
			WorkflowExecutorRegistry executors,
			WorkflowExecutionAuditService executionAudit) {
		this.instances = instances;
		this.nodes = nodes;
		this.executors = executors;
		this.executionAudit = executionAudit;
	}

	@Override
	public void execute(DelegateExecution execution) {
		String chronosInstanceId = String.valueOf(execution.getVariable("chronosInstanceId"));
		WorkflowInstance instance = instances.findById(chronosInstanceId)
				.orElseThrow(() -> new IllegalStateException("Chronos 流程实例不存在：" + chronosInstanceId));
		WorkflowNode node = nodes.findByFlowIdAndNodeKey(
				instance.getDefinitionId(),
				execution.getCurrentActivityId())
				.orElseThrow(() -> new IllegalStateException(
						"自动节点不存在：" + execution.getCurrentActivityId()));
		String requestJson;
		try {
			requestJson = json.writeValueAsString(execution.getVariables());
		} catch (Exception exception) {
			throw new IllegalStateException("流程变量无法序列化", exception);
		}
		String logId = executionAudit.started(
				chronosInstanceId,
				execution.getProcessInstanceId(),
				node,
				executors.redact(requestJson));
		try {
			// 执行器输入直接取 Flowable 变量，输出再写回引擎，后续网关可立即使用。
			String result = executors.execute(
					node,
					requestJson,
					chronosInstanceId,
					execution.getId());
			Map<String, Object> output = json.readValue(result, MAP_TYPE);
			execution.setVariables(output);
			instance.setVariablesJson(result);
			instances.save(instance);
			executionAudit.succeeded(logId, executors.redact(result));
		} catch (IllegalArgumentException exception) {
			executionAudit.failed(logId, exception);
			throw exception;
		} catch (Exception exception) {
			executionAudit.failed(logId, exception);
			throw new IllegalStateException("自动节点执行失败：" + node.getNodeName(), exception);
		}
	}
}
