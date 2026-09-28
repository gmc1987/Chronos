package com.chronos.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Bulk approval orchestrates existing single-task commands. Each call crosses
 * the WorkflowService transaction proxy, so one rejected/stale task cannot
 * roll back successful approvals for unrelated workflow instances.
 */
@Service
public class WorkflowBatchApprovalService {
	private static final int MAX_BATCH_SIZE = 20;
	private final WorkflowService workflows;

	public WorkflowBatchApprovalService(WorkflowService workflows) {
		this.workflows = workflows;
	}

	public BatchResult approve(BatchCommand command, String actor) {
		if (command == null || command.taskIds() == null || command.taskIds().isEmpty()) {
			throw new IllegalArgumentException("请选择要办理的任务");
		}
		if (command.taskIds().size() > MAX_BATCH_SIZE) {
			throw new IllegalArgumentException("单次最多办理 20 个任务");
		}
		Set<String> unique = new HashSet<>();
		for (String id : command.taskIds()) {
			if (id == null || id.isBlank() || !unique.add(id)) {
				throw new IllegalArgumentException("任务 ID 不能为空或重复");
			}
		}
		List<BatchItemResult> items = new ArrayList<>();
		for (String id : command.taskIds()) {
			try {
				var instance = workflows.completeTask(id, true, command.comment(), actor);
				items.add(new BatchItemResult(id, "SUCCEEDED", instance.getId(), null));
			} catch (AccessDeniedException exception) {
				// Do not disclose task ownership or instance data to an unauthorized actor.
				items.add(new BatchItemResult(id, "DENIED", null, "无权办理该任务"));
			} catch (RuntimeException exception) {
				items.add(new BatchItemResult(id, "FAILED", null, safeMessage(exception)));
			}
		}
		long succeeded = items.stream().filter(item -> "SUCCEEDED".equals(item.status())).count();
		return new BatchResult(command.taskIds().size(), succeeded, items);
	}

	private String safeMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (exception instanceof IllegalArgumentException && message != null && !message.isBlank()) {
			return message.length() > 200 ? message.substring(0, 200) : message;
		}
		return "任务办理失败，请单独查看该任务";
	}

	public record BatchCommand(List<String> taskIds, String comment) {
	}

	public record BatchItemResult(String taskId, String status, String instanceId, String message) {
	}

	public record BatchResult(long total, long succeeded, List<BatchItemResult> items) {
	}
}
