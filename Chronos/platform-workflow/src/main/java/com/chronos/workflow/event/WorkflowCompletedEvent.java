package com.chronos.workflow.event;

import java.util.Map;

/**
 * 流程完成后的稳定业务集成事件。
 *
 * 事件只携带业务模块需要的不可变快照，避免业务监听器反向依赖工作流内部实体。
 */
public record WorkflowCompletedEvent(
		String instanceId,
		String definitionId,
		String flowCode,
		String businessKey,
		String initiatedBy,
		String completedBy,
		Map<String, Object> mainFormData) {
}
