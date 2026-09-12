package com.chronos.workflow.event;

import java.util.Map;

/** Emitted when a workflow instance is explicitly terminated by rejection. */
public record WorkflowRejectedEvent(String instanceId, String definitionId, String flowCode,
		String businessKey, String initiatedBy, String rejectedBy, String comment,
		Map<String, Object> mainFormData) {}
