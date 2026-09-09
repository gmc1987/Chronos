package com.chronos.workflow.executor;

import com.chronos.Idao.workflow.IWorkflowExecutionLogRepository;
import com.chronos.model.workflow.WorkflowExecutionLog;
import com.chronos.model.workflow.WorkflowNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 使用独立事务保留失败日志，避免随 Flowable 作业事务一起回滚。 */
@Service
public class WorkflowExecutionAuditService {
	private final IWorkflowExecutionLogRepository logs;

	public WorkflowExecutionAuditService(IWorkflowExecutionLogRepository logs) {
		this.logs = logs;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public String started(
			String instanceId,
			String engineInstanceId,
			WorkflowNode node,
			String requestJson) {
		WorkflowExecutionLog log = new WorkflowExecutionLog();
		log.setInstanceId(instanceId);
		log.setEngineInstanceId(engineInstanceId);
		log.setNodeId(node.getId());
		log.setNodeKey(node.getNodeKey());
		log.setExecutor(node.getExecutor());
		log.setStatus("RUNNING");
		log.setStartedAt(LocalDateTime.now());
		log.setRequestJson(requestJson);
		return logs.saveAndFlush(log).getId();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void succeeded(String logId, String responseJson) {
		WorkflowExecutionLog log = require(logId);
		finish(log);
		log.setStatus("SUCCEEDED");
		log.setResponseJson(responseJson);
		logs.saveAndFlush(log);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failed(String logId, Throwable error) {
		WorkflowExecutionLog log = require(logId);
		finish(log);
		log.setStatus("FAILED");
		log.setErrorMessage(limit(error.getMessage(), 8000));
		logs.saveAndFlush(log);
	}

	@Transactional(readOnly = true)
	public List<WorkflowExecutionLog> list(String instanceId) {
		if (instanceId == null || instanceId.isBlank()) {
			return logs.findTop100ByOrderByCreateTimeDesc();
		}
		return logs.findTop100ByInstanceIdOrderByCreateTimeDesc(instanceId);
	}

	private WorkflowExecutionLog require(String id) {
		return logs.findById(id)
				.orElseThrow(() -> new IllegalStateException("自动节点执行日志不存在：" + id));
	}

	private void finish(WorkflowExecutionLog log) {
		LocalDateTime now = LocalDateTime.now();
		log.setFinishedAt(now);
		log.setDurationMs(Duration.between(log.getStartedAt(), now).toMillis());
	}

	private String limit(String value, int maximum) {
		if (value == null) {
			return "unknown error";
		}
		return value.length() <= maximum ? value : value.substring(0, maximum);
	}
}
