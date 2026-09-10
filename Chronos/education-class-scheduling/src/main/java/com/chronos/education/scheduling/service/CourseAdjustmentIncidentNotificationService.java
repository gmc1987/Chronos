package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.workflow.WorkflowNotificationService;
import org.springframework.stereotype.Service;

/** 将调课回写事故可靠通知给具备事故处置权限的有效管理员。 */
@Service
public class CourseAdjustmentIncidentNotificationService {
	private static final String MANAGE_PERMISSION = "education:scheduling:manage";
	private final IAdminUserRepository users;
	private final WorkflowNotificationService notifications;

	public CourseAdjustmentIncidentNotificationService(
			IAdminUserRepository users,
			WorkflowNotificationService notifications) {
		this.users = users;
		this.notifications = notifications;
	}

	public void enqueueFailure(CourseAdjustmentRecord record, String occurrenceKey) {
		String content = "调课审批已完成，但课表自动回写失败。流程实例："
				+ record.getWorkflowInstanceId()
				+ "；原因："
				+ record.getMessage()
				+ "。请进入走班排课的调课回写异常页处理。";
		for (String recipient : users.findActiveUsernamesByPermissionCode(MANAGE_PERMISSION)) {
			notifications.enqueueUserEvent(
					"EDUCATION_COURSE_ADJUSTMENT_INCIDENT",
					record.getId(),
					recipient,
					"调课回写异常待处理",
					content,
					occurrenceKey);
		}
	}
}
