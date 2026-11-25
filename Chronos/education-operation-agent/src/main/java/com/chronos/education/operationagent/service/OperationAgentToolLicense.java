package com.chronos.education.operationagent.service;

import org.springframework.security.access.AccessDeniedException;

/** Product boundary for customer deployments. Business permissions remain enforced by the agent and tools. */
@FunctionalInterface
public interface OperationAgentToolLicense {
	boolean allows(String toolCode);

	default void require(String toolCode) {
		if (!allows(toolCode)) throw new AccessDeniedException("当前部署未开通该智能体工具所属业务");
	}
}
