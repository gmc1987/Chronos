package com.chronos.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.workflow.IWorkflowIncidentRepository;
import com.chronos.Idao.workflow.IWorkflowOutboxRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;

/** Global operator counters. This service never returns individual task payloads. */
@Service
public class WorkflowOperationsService {
	private static final Logger log = LoggerFactory.getLogger(WorkflowOperationsService.class);
	private static final List<String> OPEN_TASK_STATES = List.of("PENDING", "CLAIMABLE");

	private final IWorkflowTaskRepository tasks;
	private final IWorkflowIncidentRepository incidents;
	private final IWorkflowOutboxRepository outbox;
	private final int maximumOldTasks;
	private final int maximumDeadEvents;
	private final int oldTaskHours;
	private final AtomicReference<String> lastAlert = new AtomicReference<>("");

	public WorkflowOperationsService(
			IWorkflowTaskRepository tasks,
			IWorkflowIncidentRepository incidents,
			IWorkflowOutboxRepository outbox,
			@Value("${chronos.workflow.operations.max-old-tasks:20}") int maximumOldTasks,
			@Value("${chronos.workflow.operations.max-dead-events:0}") int maximumDeadEvents,
			@Value("${chronos.workflow.operations.old-task-hours:24}") int oldTaskHours) {
		this.tasks = tasks;
		this.incidents = incidents;
		this.outbox = outbox;
		this.maximumOldTasks = Math.max(0, maximumOldTasks);
		this.maximumDeadEvents = Math.max(0, maximumDeadEvents);
		this.oldTaskHours = Math.max(1, oldTaskHours);
	}

	@Transactional(readOnly = true)
	public OperationsHealth health() {
		LocalDateTime now = LocalDateTime.now();
		long oldTasks = tasks.countByStatusInAndCreateTimeBefore(
				OPEN_TASK_STATES,
				now.minusHours(oldTaskHours));
		long overdueTasks = tasks.countByStatusInAndDueAtBefore(OPEN_TASK_STATES, now);
		long openIncidents = incidents.countByStatus("OPEN");
		long pendingEvents = outbox.countByStatus("PENDING");
		long deadEvents = outbox.countByStatus("DEAD");
		boolean alert = oldTasks > maximumOldTasks
				|| deadEvents > maximumDeadEvents
				|| openIncidents > 0;
		return new OperationsHealth(
				now,
				oldTaskHours,
				oldTasks,
				overdueTasks,
				openIncidents,
				pendingEvents,
				deadEvents,
				alert);
	}

	/** Log only state transitions; metrics polling must not flood production logs. */
	@Scheduled(fixedDelayString = "${chronos.workflow.operations.scan-ms:60000}")
	public void scan() {
		try {
			OperationsHealth current = health();
			String state = current.alert() ? "ALERT" : "HEALTHY";
			if (!state.equals(lastAlert.getAndSet(state))) {
				if (current.alert()) {
					log.warn("Workflow operations alert: oldTasks={}, overdueTasks={}, incidents={}, pendingEvents={}, deadEvents={}",
							current.oldTasks(),
							current.overdueTasks(),
							current.openIncidents(),
							current.pendingEvents(),
							current.deadEvents());
				} else {
					log.info("Workflow operations recovered");
				}
			}
		} catch (RuntimeException exception) {
			log.error("Workflow operations scan failed", exception);
		}
	}

	public record OperationsHealth(
			LocalDateTime checkedAt,
			int oldTaskHours,
			long oldTasks,
			long overdueTasks,
			long openIncidents,
			long pendingEvents,
			long deadEvents,
			boolean alert) {
	}
}
