package com.chronos.workflow.event;

import java.util.Map;

/** Published in the same transaction as the new workflow instance. */
public record WorkflowStartedEvent(String instanceId, String flowCode, String actor,
		Map<String, Object> mainFormData, Map<String, Object> runtimeVariables) {}
