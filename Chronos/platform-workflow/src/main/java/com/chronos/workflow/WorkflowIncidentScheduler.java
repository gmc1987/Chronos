package com.chronos.workflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期将 Flowable 死信同步到事故中心，扫描失败不会影响引擎自己的重试事务。 */
@Component
public class WorkflowIncidentScheduler {
	private final WorkflowIncidentService incidents;

	public WorkflowIncidentScheduler(WorkflowIncidentService incidents) {
		this.incidents = incidents;
	}

	@Scheduled(fixedDelayString = "${chronos.workflow.incident-scan-ms:30000}")
	public void synchronize() {
		incidents.synchronizeDeadLetters();
	}
}
