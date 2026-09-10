package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.CourseAdjustmentRecordRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 调课落地与重放服务。入口使用独立事务，防止失败记录随课表回写一起回滚。 */
@Service
public class CourseAdjustmentApplicationService {
	private final ScheduleEntryRepository entries;
	private final CourseAdjustmentRecordRepository records;
	private final ClassSchedulingService scheduling;
	private final CourseAdjustmentStartValidator startValidator;
	private final CourseAdjustmentIncidentNotificationService incidentNotifications;
	private final IAuditLogService audit;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public CourseAdjustmentApplicationService(
			ScheduleEntryRepository entries,
			CourseAdjustmentRecordRepository records,
			ClassSchedulingService scheduling,
			CourseAdjustmentStartValidator startValidator,
			CourseAdjustmentIncidentNotificationService incidentNotifications,
			IAuditLogService audit) {
		this.entries = entries;
		this.records = records;
		this.scheduling = scheduling;
		this.startValidator = startValidator;
		this.incidentNotifications = incidentNotifications;
		this.audit = audit;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void apply(WorkflowCompletedEvent event) {
		CourseAdjustmentRecord existing = records.findByWorkflowInstanceId(event.instanceId()).orElse(null);
		if (existing != null && "APPLIED".equals(existing.getStatus())) {
			return;
		}
		CourseAdjustmentRecord record = existing == null ? new CourseAdjustmentRecord() : existing;
		record.setWorkflowInstanceId(event.instanceId());
		record.setBusinessKey(event.businessKey());
		record.setRequestPayload(writePayload(event.mainFormData()));
		// 审批人可能拥有主表单编辑权，因此完成时再次按发起人范围校验最终快照。
		startValidator.validate(event.initiatedBy(), event.mainFormData());
		applyRecord(record, event.mainFormData(), event.completedBy());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(WorkflowCompletedEvent event, RuntimeException exception) {
		CourseAdjustmentRecord record = records.findByWorkflowInstanceId(event.instanceId())
				.orElseGet(CourseAdjustmentRecord::new);
		record.setWorkflowInstanceId(event.instanceId());
		record.setBusinessKey(event.businessKey());
		record.setScheduleEntryId(text(event.mainFormData().get("scheduleEntryId"), "UNKNOWN"));
		record.setAdjustmentType(text(event.mainFormData().get("adjustmentType"), "UNKNOWN").toUpperCase());
		record.setRequestPayload(writePayload(event.mainFormData()));
		record.setStatus("FAILED");
		record.setMessage(limit(exception.getMessage(), 1000));
		record = records.save(record);
		// 事故记录和可靠 Outbox 在同一事务提交，避免出现有事故但管理员未收到消息。
		incidentNotifications.enqueueFailure(record, "INITIAL");
		audit.log(event.completedBy(), "EDUCATION_COURSE_ADJUSTMENT_FAILED",
				"workflowInstanceId=" + event.instanceId() + ", error=" + limit(exception.getMessage(), 500));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CourseAdjustmentRecord retry(String recordId, String actor) {
		// 同一事故的重放必须串行化，防止两个管理员同时重复修改正式课表。
		CourseAdjustmentRecord record = records.findLockedById(recordId)
				.orElseThrow(() -> new IllegalArgumentException("调课事故不存在"));
		if ("APPLIED".equals(record.getStatus())) {
			return record;
		}
		record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
		record.setLastRetryBy(actor);
		applyRecord(record, readPayload(record.getRequestPayload()), actor);
		return record;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CourseAdjustmentRecord recordRetryFailure(
			String recordId,
			String actor,
			RuntimeException exception) {
		CourseAdjustmentRecord record = records.findLockedById(recordId)
				.orElseThrow(() -> new IllegalArgumentException("调课事故不存在"));
		// 另一并发请求可能已经成功，失败请求不能把 APPLIED 状态覆盖回 FAILED。
		if ("APPLIED".equals(record.getStatus())) {
			return record;
		}
		record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
		record.setLastRetryBy(actor);
		record.setStatus("FAILED");
		record.setMessage(limit(exception.getMessage(), 1000));
		record = records.save(record);
		incidentNotifications.enqueueFailure(record, "RETRY-" + record.getRetryCount());
		audit.log(actor, "EDUCATION_COURSE_ADJUSTMENT_RETRY_FAILED",
				"recordId=" + recordId + ", error=" + limit(exception.getMessage(), 500));
		return record;
	}

	@Transactional(readOnly = true)
	public List<CourseAdjustmentRecord> failures() {
		return records.findByStatusOrderByCreateTimeDesc("FAILED");
	}

	@Transactional(readOnly = true)
	public Page<CourseAdjustmentRecord> incidents(
			String status,
			String adjustmentType,
			String keyword,
			int page,
			int size) {
		Specification<CourseAdjustmentRecord> specification = (root, query, builder) -> {
			var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
			if (hasText(status) && !"ALL".equalsIgnoreCase(status)) {
				predicates.add(builder.equal(
						builder.upper(root.get("status")),
						status.trim().toUpperCase()));
			}
			if (hasText(adjustmentType)) {
				predicates.add(builder.equal(
						builder.upper(root.get("adjustmentType")),
						adjustmentType.trim().toUpperCase()));
			}
			if (hasText(keyword)) {
				String pattern = "%" + keyword.trim().toLowerCase() + "%";
				predicates.add(builder.or(
						builder.like(builder.lower(root.get("workflowInstanceId")), pattern),
						builder.like(builder.lower(root.get("businessKey")), pattern),
						builder.like(builder.lower(root.get("scheduleEntryId")), pattern),
						builder.like(builder.lower(root.get("message")), pattern)));
			}
			return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
		return records.findAll(
				specification,
				PageRequest.of(
						Math.max(page, 0),
						Math.min(Math.max(size, 1), 100),
						Sort.by(Sort.Direction.DESC, "createTime")));
	}

	private void applyRecord(CourseAdjustmentRecord record, Map<String, Object> form, String actor) {
		String sourceId = required(form, "scheduleEntryId", "原课表项不能为空");
		String type = required(form, "adjustmentType", "调整类型不能为空").toUpperCase();
		ScheduleEntry source = entries.findById(sourceId)
				.orElseThrow(() -> new IllegalArgumentException("原课表项不存在"));
		record.setScheduleEntryId(sourceId);
		record.setAdjustmentType(type);
		record.setStatus("APPLYING");
		record.setMessage(null);
		records.saveAndFlush(record);

		String resultEntryId = switch (type) {
			case "MOVE" -> move(source, form, record.getWorkflowInstanceId());
			case "CANCEL" -> cancel(source, record.getWorkflowInstanceId());
			case "SUBSTITUTE" -> substitute(source, form, record.getWorkflowInstanceId());
			case "MAKEUP" -> makeup(source, form, record.getWorkflowInstanceId());
			default -> throw new IllegalArgumentException("不支持的调整类型：" + type);
		};
		record.setResultEntryId(resultEntryId);
		record.setStatus("APPLIED");
		record.setMessage("审批完成后已自动回写课表");
		records.save(record);
		audit.log(actor, "EDUCATION_COURSE_ADJUSTMENT_APPLY",
				"workflowInstanceId=" + record.getWorkflowInstanceId()
						+ ", type=" + type + ", entryId=" + resultEntryId);
	}

	private String move(ScheduleEntry source, Map<String, Object> form, String instanceId) {
		String id = scheduling.saveEntry(source.getId(), command(source, form)).id();
		ScheduleEntry updated = entries.findById(id).orElseThrow();
		updated.setSourceAdjustmentInstanceId(instanceId);
		return entries.save(updated).getId();
	}

	private String cancel(ScheduleEntry source, String instanceId) {
		source.setStatus("CANCELLED");
		source.setSourceAdjustmentInstanceId(instanceId);
		return entries.save(source).getId();
	}

	private String substitute(ScheduleEntry source, Map<String, Object> form, String instanceId) {
		source.setSubstituteTeacherId(required(form, "substituteTeacherId", "代课教师不能为空"));
		source.setStatus("SUBSTITUTED");
		source.setSourceAdjustmentInstanceId(instanceId);
		return entries.save(source).getId();
	}

	private String makeup(ScheduleEntry source, Map<String, Object> form, String instanceId) {
		String id = scheduling.saveEntry(null, command(source, form)).id();
		ScheduleEntry created = entries.findById(id).orElseThrow();
		created.setStatus("MAKEUP");
		created.setSourceAdjustmentInstanceId(instanceId);
		return entries.save(created).getId();
	}

	private ScheduleEntryCommand command(ScheduleEntry source, Map<String, Object> form) {
		return new ScheduleEntryCommand(
				source.getSemesterCode(),
				source.getOfferingId(),
				text(form.get("targetClassroomId"), source.getClassroomId()),
				number(form.get("targetDayOfWeek"), source.getDayOfWeek()),
				number(form.get("targetPeriodNo"), source.getPeriodNo()),
				source.getDurationPeriods(),
				source.getWeekPattern(),
				source.getStartWeek(),
				source.getEndWeek(),
				source.getLocked());
	}

	private String writePayload(Map<String, Object> payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("调课表单快照序列化失败", exception);
		}
	}

	private Map<String, Object> readPayload(String payload) {
		try {
			return objectMapper.readValue(payload, new TypeReference<>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("调课表单快照无法解析", exception);
		}
	}

	private String required(Map<String, Object> form, String key, String message) {
		String value = text(form.get(key), null);
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private String text(Object value, String fallback) {
		return value == null || String.valueOf(value).isBlank()
				? fallback
				: String.valueOf(value).trim();
	}

	private Integer number(Object value, Integer fallback) {
		return value == null || String.valueOf(value).isBlank()
				? fallback
				: Integer.valueOf(String.valueOf(value));
	}

	private String limit(String value, int maximum) {
		if (value == null) {
			return "未知错误";
		}
		return value.length() <= maximum ? value : value.substring(0, maximum);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
