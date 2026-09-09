package com.chronos.workflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class WorkflowSlaScheduler {
	private final WorkflowSlaService sla;
	private final WorkflowNotificationService notifications;

	public WorkflowSlaScheduler(WorkflowSlaService sla, WorkflowNotificationService notifications) {
		this.sla = sla;
		this.notifications = notifications;
	}

	@Scheduled(fixedDelayString = "${chronos.workflow.sla-scan-ms:60000}")
	public void scan() {
		sla.scan();
	}

	@Scheduled(fixedDelayString = "${chronos.workflow.outbox-dispatch-ms:5000}")
	public void dispatchNotifications() {
		notifications.dispatchPending();
	}
}
