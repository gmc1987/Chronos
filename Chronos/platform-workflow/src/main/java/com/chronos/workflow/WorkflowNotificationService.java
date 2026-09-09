package com.chronos.workflow;

import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowNotificationRepository;
import com.chronos.Idao.workflow.IWorkflowOutboxRepository;
import com.chronos.Idao.workflow.IWorkflowTaskCandidateRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNotification;
import com.chronos.model.workflow.WorkflowOutbox;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.model.workflow.WorkflowTaskCandidate;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/** SLA 事件入箱、可靠投递和门户通知查询。 */
@Service
public class WorkflowNotificationService {
	private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {
	};

	private final IWorkflowOutboxRepository outbox;
	private final IWorkflowNotificationRepository notifications;
	private final IWorkflowTaskRepository tasks;
	private final IWorkflowTaskCandidateRepository candidates;
	private final IWorkflowInstanceRepository instances;
	private final IAuditLogService audit;
	private final ObjectMapper json = new ObjectMapper();

	public WorkflowNotificationService(
			IWorkflowOutboxRepository outbox,
			IWorkflowNotificationRepository notifications,
			IWorkflowTaskRepository tasks,
			IWorkflowTaskCandidateRepository candidates,
			IWorkflowInstanceRepository instances,
			IAuditLogService audit) {
		this.outbox = outbox;
		this.notifications = notifications;
		this.tasks = tasks;
		this.candidates = candidates;
		this.instances = instances;
		this.audit = audit;
	}

	@Transactional
	public void enqueueTaskEvent(
			WorkflowTask task,
			String eventType,
			String title,
			String content,
			String occurrenceKey,
			String additionalRecipient) {
		WorkflowInstance instance = instances.findById(task.getInstanceId()).orElseThrow();
		Set<String> recipients = new LinkedHashSet<>();
		if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
			recipients.add(task.getAssignee());
		} else {
			candidates.findByTaskId(task.getId()).stream()
					.filter(candidate -> "USER".equals(candidate.getSubjectType()))
					.map(WorkflowTaskCandidate::getSubjectId)
					.forEach(recipients::add);
		}
		if (additionalRecipient != null && !additionalRecipient.isBlank()) {
			recipients.add(additionalRecipient);
		}
		for (String recipient : recipients) {
			String deduplicationKey = eventType + ":" + task.getId() + ":" + occurrenceKey + ":" + recipient;
			if (outbox.existsByDeduplicationKey(deduplicationKey)) {
				continue;
			}
			WorkflowOutbox event = new WorkflowOutbox();
			event.setEventType(eventType);
			event.setAggregateId(task.getId());
			event.setDeduplicationKey(deduplicationKey);
			event.setNextAttemptAt(LocalDateTime.now());
			event.setPayloadJson(write(Map.of(
					"recipient", recipient,
					"title", title,
					"content", content,
					"instanceId", instance.getId(),
					"taskId", task.getId())));
			outbox.save(event);
		}
	}

	/**
	 * 消息自动节点也通过 Outbox 投递，确保流程事务提交后才产生通知，
	 * 并复用第四阶段的集群抢占、失败重试和死信运维能力。
	 */
	@Transactional
	public void enqueueWorkflowMessage(
			String instanceId,
			String nodeKey,
			String recipient,
			String title,
			String content,
			String idempotencyKey) {
		String deduplicationKey = "WORKFLOW_MESSAGE:"
				+ instanceId
				+ ":"
				+ nodeKey
				+ ":"
				+ idempotencyKey
				+ ":"
				+ recipient;
		if (outbox.existsByDeduplicationKey(deduplicationKey)) {
			return;
		}
		WorkflowOutbox event = new WorkflowOutbox();
		event.setEventType("WORKFLOW_MESSAGE");
		event.setAggregateId(instanceId);
		event.setDeduplicationKey(deduplicationKey);
		event.setNextAttemptAt(LocalDateTime.now());
		event.setPayloadJson(write(Map.of(
				"recipient", recipient,
				"title", title,
				"content", content,
				"instanceId", instanceId,
				"taskId", "")));
		outbox.save(event);
	}

	@Transactional
	public void dispatchPending() {
		List<WorkflowOutbox> events = outbox.lockDispatchBatch(LocalDateTime.now());
		for (WorkflowOutbox event : events) {
			try {
				deliver(event);
				event.setStatus("SENT");
				event.setSentAt(LocalDateTime.now());
				event.setLastError(null);
			} catch (Exception exception) {
				int attempts = event.getAttempts() + 1;
				event.setAttempts(attempts);
				event.setLastError(limit(exception.getMessage(), 1000));
				if (attempts >= 10) {
					event.setStatus("DEAD");
				} else {
					event.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << attempts)));
				}
			}
			outbox.save(event);
		}
	}

	@Transactional(readOnly = true)
	public List<WorkflowNotification> list(String actor) {
		return notifications.findTop100ByRecipientOrderByCreateTimeDesc(actor);
	}

	@Transactional(readOnly = true)
	public Page<WorkflowNotification> list(String actor, int page, int size) {
		return notifications.findByRecipientOrderByCreateTimeDesc(actor, pageable(page, size));
	}

	@Transactional(readOnly = true)
	public long unreadCount(String actor) {
		return notifications.countByRecipientAndReadAtIsNull(actor);
	}

	@Transactional
	public int readAll(String actor) {
		List<WorkflowNotification> unread = notifications.findByRecipientAndReadAtIsNull(actor);
		LocalDateTime now = LocalDateTime.now();
		unread.forEach(notification -> notification.setReadAt(now));
		notifications.saveAll(unread);
		return unread.size();
	}

	@Transactional(readOnly = true)
	public List<WorkflowOutbox> deadEvents() {
		return outbox.findTop100ByStatusOrderByCreateTimeDesc("DEAD");
	}

	@Transactional(readOnly = true)
	public Page<WorkflowOutbox> deadEvents(int page, int size) {
		return outbox.findByStatusOrderByCreateTimeDesc("DEAD", pageable(page, size));
	}

	@Transactional
	public WorkflowOutbox retryDeadEvent(String id, String actor) {
		WorkflowOutbox event = requireDeadEvent(id);
		event.setStatus("PENDING");
		event.setAttempts(0);
		event.setLastError(null);
		event.setNextAttemptAt(LocalDateTime.now());
		event = outbox.save(event);
		audit.log(actor, "WORKFLOW_OUTBOX_RETRY", "eventId=" + id);
		return event;
	}

	@Transactional
	public WorkflowOutbox ignoreDeadEvent(String id, String actor) {
		WorkflowOutbox event = requireDeadEvent(id);
		event.setStatus("IGNORED");
		event = outbox.save(event);
		audit.log(actor, "WORKFLOW_OUTBOX_IGNORE", "eventId=" + id);
		return event;
	}

	@Transactional
	public WorkflowNotification read(String id, String actor) {
		WorkflowNotification notification = notifications.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("通知不存在"));
		if (!actor.equals(notification.getRecipient())) {
			throw new AccessDeniedException("无权读取该通知");
		}
		if (notification.getReadAt() == null) {
			notification.setReadAt(LocalDateTime.now());
		}
		return notifications.save(notification);
	}

	private void deliver(WorkflowOutbox event) throws Exception {
		if (notifications.existsBySourceEventId(event.getId())) {
			return;
		}
		Map<String, String> payload = json.readValue(event.getPayloadJson(), PAYLOAD_TYPE);
		WorkflowNotification notification = new WorkflowNotification();
		notification.setRecipient(payload.get("recipient"));
		notification.setNotificationType(event.getEventType());
		notification.setTitle(payload.get("title"));
		notification.setContent(payload.get("content"));
		notification.setInstanceId(payload.get("instanceId"));
		notification.setTaskId(payload.get("taskId"));
		notification.setSourceEventId(event.getId());
		notifications.save(notification);
	}

	private WorkflowOutbox requireDeadEvent(String id) {
		WorkflowOutbox event = outbox.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Outbox 事件不存在"));
		if (!"DEAD".equals(event.getStatus())) {
			throw new IllegalArgumentException("仅死信事件允许执行该操作");
		}
		return event;
	}

	private String write(Map<String, String> value) {
		try {
			return json.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalArgumentException("通知事件序列化失败", exception);
		}
	}

	private String limit(String value, int maximum) {
		if (value == null) {
			return "unknown error";
		}
		return value.length() <= maximum ? value : value.substring(0, maximum);
	}

	private Pageable pageable(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
	}
}
