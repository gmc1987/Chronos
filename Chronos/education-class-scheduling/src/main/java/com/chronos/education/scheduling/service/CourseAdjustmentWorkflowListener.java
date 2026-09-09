package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.CourseAdjustmentRecordRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 将已审批的调课申请幂等回写到正式课表。 */
@Component
public class CourseAdjustmentWorkflowListener {
	private static final String FLOW_CODE = "EDU_COURSE_ADJUSTMENT_APPROVAL";

	private final ScheduleEntryRepository entries;
	private final CourseAdjustmentRecordRepository records;
	private final ClassSchedulingService scheduling;
	private final IAuditLogService audit;

	public CourseAdjustmentWorkflowListener(
			ScheduleEntryRepository entries,
			CourseAdjustmentRecordRepository records,
			ClassSchedulingService scheduling,
			IAuditLogService audit) {
		this.entries = entries;
		this.records = records;
		this.scheduling = scheduling;
		this.audit = audit;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onWorkflowCompleted(WorkflowCompletedEvent event) {
		if (!FLOW_CODE.equals(event.flowCode())
				|| records.existsByWorkflowInstanceId(event.instanceId())) {
			return;
		}

		Map<String, Object> form = event.mainFormData();
		String sourceId = required(form, "scheduleEntryId", "原课表项不能为空");
		String type = required(form, "adjustmentType", "调整类型不能为空").toUpperCase();
		ScheduleEntry source = entries.findById(sourceId)
				.orElseThrow(() -> new IllegalArgumentException("原课表项不存在"));

		CourseAdjustmentRecord record = new CourseAdjustmentRecord();
		record.setWorkflowInstanceId(event.instanceId());
		record.setBusinessKey(event.businessKey());
		record.setScheduleEntryId(sourceId);
		record.setAdjustmentType(type);
		record.setStatus("APPLYING");
		record = records.saveAndFlush(record);

		String resultEntryId = switch (type) {
			case "MOVE" -> move(source, form, event.instanceId());
			case "CANCEL" -> cancel(source, event.instanceId());
			case "SUBSTITUTE" -> substitute(source, form, event.instanceId());
			case "MAKEUP" -> makeup(source, form, event.instanceId());
			default -> throw new IllegalArgumentException("不支持的调整类型：" + type);
		};
		record.setResultEntryId(resultEntryId);
		record.setStatus("APPLIED");
		record.setMessage("审批完成后已自动回写课表");
		records.save(record);
		audit.log(event.completedBy(), "EDUCATION_COURSE_ADJUSTMENT_APPLY",
				"workflowInstanceId=" + event.instanceId() + ", type=" + type + ", entryId=" + resultEntryId);
	}

	private String move(ScheduleEntry source, Map<String, Object> form, String instanceId) {
		ScheduleEntryCommand command = command(source, form);
		String id = scheduling.saveEntry(source.getId(), command).id();
		ScheduleEntry updated = entries.findById(id).orElseThrow();
		updated.setSourceAdjustmentInstanceId(instanceId);
		entries.save(updated);
		return id;
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
}
