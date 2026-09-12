package com.chronos.education.scheduling.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.workflow.WorkflowNotificationService;

/** 发布被质量门禁拦截时，以独立事务通知操作人，避免异常回滚时丢失提醒。 */
@Service
public class ScheduleQualityRiskNotificationService {
	private final WorkflowNotificationService notifications;

	public ScheduleQualityRiskNotificationService(WorkflowNotificationService notifications) {
		this.notifications = notifications;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void notifyBlocked(String actor, String semesterCode, List<String> blockers) {
		notifications.enqueueUserEvent(
				"EDUCATION_SCHEDULE_QUALITY_BLOCKED",
				semesterCode,
				actor,
				"课表发布被质量门禁拦截",
				semesterCode + " 学期课表存在 " + blockers.size()
						+ " 项硬风险：" + String.join("；", blockers),
				semesterCode);
	}
}
