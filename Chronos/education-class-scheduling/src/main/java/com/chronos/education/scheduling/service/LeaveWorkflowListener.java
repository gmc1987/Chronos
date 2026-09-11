package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowNotificationService;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 审批完成后生成请假业务台账，并通过工作流 Outbox 可靠通知发起人。 */
@Component
public class LeaveWorkflowListener {
	private final LeaveRequestRecordRepository records;
	private final WorkflowNotificationService notifications;
	private final IAuditLogService audit;
	private final EducationApplicantResolver applicants;

	public LeaveWorkflowListener(
			LeaveRequestRecordRepository records,
			WorkflowNotificationService notifications,
			IAuditLogService audit,
			EducationApplicantResolver applicants) {
		this.records = records;
		this.notifications = notifications;
		this.audit = audit;
		this.applicants = applicants;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onWorkflowCompleted(WorkflowCompletedEvent event) {
		String applicantType = applicantType(event.flowCode());
		if (applicantType == null || records.existsByWorkflowInstanceId(event.instanceId())) {
			return;
		}
		Map<String, Object> form = event.mainFormData();
		LeaveRequestRecord record = new LeaveRequestRecord();
		record.setWorkflowInstanceId(event.instanceId());
		record.setBusinessKey(event.businessKey());
		record.setApplicantType(applicantType);
		// 申请人必须由认证账号映射，不能依赖可被客户端篡改、也可能未配置的隐藏表单字段。
		record.setApplicantId(applicants.resolve(event.initiatedBy(), applicantType));
		record.setLeaveType(required(form, "leaveType"));
		record.setStartDate(LocalDate.parse(required(form, "startDate")));
		record.setEndDate(LocalDate.parse(required(form, "endDate")));
		record.setReason(required(form, "reason"));
		record.setStatus("APPROVED");
		record.setApprovedBy(event.completedBy());
		records.save(record);

		notifications.enqueueWorkflowMessage(
				event.instanceId(),
				"_COMPLETED",
				event.initiatedBy(),
				"请假申请已审批通过",
				"申请单 " + event.businessKey() + " 已审批通过。",
				"LEAVE_APPROVED");
		audit.log(event.completedBy(), "EDUCATION_LEAVE_APPROVED",
				"workflowInstanceId=" + event.instanceId() + ", applicantType=" + applicantType);
	}

	private String applicantType(String flowCode) {
		if ("EDU_STUDENT_LEAVE_APPROVAL".equals(flowCode)) {
			return "STUDENT";
		}
		if ("EDU_TEACHER_LEAVE_APPROVAL".equals(flowCode)) {
			return "TEACHER";
		}
		return null;
	}

	private String required(Map<String, Object> form, String key) {
		Object raw = form.get(key);
		if (raw == null || String.valueOf(raw).isBlank()) {
			throw new IllegalArgumentException("请假表单缺少字段：" + key);
		}
		return String.valueOf(raw).trim();
	}
}
