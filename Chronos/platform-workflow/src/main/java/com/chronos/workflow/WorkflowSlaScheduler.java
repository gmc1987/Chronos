package com.chronos.workflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class WorkflowSlaScheduler {
    private final WorkflowService service;
    public WorkflowSlaScheduler(WorkflowService service){this.service=service;}
    @Scheduled(fixedDelayString="${chronos.workflow.sla-scan-ms:60000}")
    public void scan(){service.markOverdueTasks();}
}
