package com.chronos.workflow;

import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一执行到期前提醒、逾期重复提醒和分级升级。 */
@Service
public class WorkflowSlaService {
	private final IWorkflowTaskRepository tasks;
	private final IWorkflowInstanceRepository instances;
	private final IWorkflowNodeRepository nodes;
	private final IWorkflowDefinitionRepository definitions;
	private final WorkflowNotificationService notifications;
	private final IAuditLogService audit;
	private final ObjectMapper json = new ObjectMapper();

	public WorkflowSlaService(
			IWorkflowTaskRepository tasks,
			IWorkflowInstanceRepository instances,
			IWorkflowNodeRepository nodes,
			IWorkflowDefinitionRepository definitions,
			WorkflowNotificationService notifications,
			IAuditLogService audit) {
		this.tasks = tasks;
		this.instances = instances;
		this.nodes = nodes;
		this.definitions = definitions;
		this.notifications = notifications;
		this.audit = audit;
	}

	@Transactional
	public void scan() {
		LocalDateTime now = LocalDateTime.now();
		for (WorkflowTask task : tasks.findByStatusInAndDueAtIsNotNull(Set.of("PENDING", "CLAIMABLE"))) {
			WorkflowInstance instance = instances.findById(task.getInstanceId()).orElse(null);
			if (instance == null || !"RUNNING".equals(instance.getStatus())) {
				continue;
			}
			WorkflowNode node = nodes.findByFlowIdAndNodeKey(instance.getDefinitionId(), task.getNodeKey()).orElse(null);
			if (node == null) {
				continue;
			}
			if (task.getDueAt().isAfter(now)) {
				markDueSoon(task, node, now);
			} else {
				markOverdue(task, instance, node, now);
			}
		}
	}

	@Transactional
	public WorkflowTask manualRemind(WorkflowTask task, String actor) {
		LocalDateTime now = LocalDateTime.now();
		if (task.getRemindedAt() != null && task.getRemindedAt().isAfter(now.minusMinutes(5))) {
			throw new IllegalArgumentException("催办过于频繁，请五分钟后再试");
		}
		task.setRemindedAt(now);
		task.setReminderCount(task.getReminderCount() + 1);
		task.setNextReminderAt(now.plusMinutes(5));
		task = tasks.save(task);
		notifications.enqueueTaskEvent(
				task,
				"TASK_REMINDER",
				"流程任务催办",
				"任务“" + task.getNodeName() + "”收到催办，请及时处理。",
				"manual-" + task.getReminderCount(),
				null);
		audit.log(actor, "WORKFLOW_TASK_REMIND", "taskId=" + task.getId()
				+ ", reminderCount=" + task.getReminderCount());
		return task;
	}

	private void markDueSoon(WorkflowTask task, WorkflowNode node, LocalDateTime now) {
		int beforeMinutes = integerProperty(node, "reminderBeforeMinutes", 60);
		if (!"NORMAL".equals(task.getSlaStatus())
				|| task.getDueAt().isAfter(now.plusMinutes(beforeMinutes))) {
			return;
		}
		task.setSlaStatus("DUE_SOON");
		tasks.save(task);
		notifications.enqueueTaskEvent(
				task,
				"TASK_DUE_SOON",
				"流程任务即将到期",
				"任务“" + task.getNodeName() + "”即将在 " + task.getDueAt() + " 到期。",
				task.getDueAt().toString(),
				null);
	}

	private void markOverdue(
			WorkflowTask task,
			WorkflowInstance instance,
			WorkflowNode node,
			LocalDateTime now) {
		long overdueMinutes = Math.max(1, Duration.between(task.getDueAt(), now).toMinutes());
		int interval = integerProperty(node, "reminderIntervalMinutes", 60);
		if (task.getNextReminderAt() == null || !task.getNextReminderAt().isAfter(now)) {
			task.setReminderCount(task.getReminderCount() + 1);
			task.setRemindedAt(now);
			task.setNextReminderAt(now.plusMinutes(interval));
			task.setSlaStatus("OVERDUE");
			tasks.save(task);
			notifications.enqueueTaskEvent(
					task,
					"TASK_OVERDUE",
					"流程任务已逾期",
					"任务“" + task.getNodeName() + "”已逾期 " + overdueMinutes + " 分钟。",
					"reminder-" + task.getReminderCount(),
					null);
		}

		int escalationInterval = integerProperty(node, "escalationIntervalMinutes", 120);
		int targetLevel = Math.min(5, (int) (overdueMinutes / escalationInterval));
		if (targetLevel > task.getEscalationLevel()) {
			WorkflowDefinition definition = definitions.findById(instance.getDefinitionId()).orElseThrow();
			String escalationUser = stringProperty(node, "escalationUser", definition.getManagerUser());
			task.setEscalationLevel(targetLevel);
			task.setSlaStatus("ESCALATED");
			tasks.save(task);
			notifications.enqueueTaskEvent(
					task,
					"TASK_ESCALATED",
					"流程任务超时升级",
					"任务“" + task.getNodeName() + "”已升级至 L" + targetLevel + "。",
					"level-" + targetLevel,
					escalationUser);
			audit.log("SYSTEM", "WORKFLOW_TASK_ESCALATE", "taskId=" + task.getId()
					+ ", level=" + targetLevel);
		}
	}

	private int integerProperty(WorkflowNode node, String key, int fallback) {
		try {
			return Math.max(1, properties(node).path(key).asInt(fallback));
		} catch (Exception exception) {
			return fallback;
		}
	}

	private String stringProperty(WorkflowNode node, String key, String fallback) {
		try {
			String value = properties(node).path(key).asText();
			return value.isBlank() ? fallback : value;
		} catch (Exception exception) {
			return fallback;
		}
	}

	private JsonNode properties(WorkflowNode node) throws Exception {
		return json.readTree(node.getPropertiesJson() == null ? "{}" : node.getPropertiesJson());
	}
}
