package com.chronos.education.scheduling.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.model.CourseAdjustmentRecord;
import com.chronos.workflow.WorkflowNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseAdjustmentIncidentNotificationServiceTest {
	@Test
	void failureIsSentOnlyToAuthorizedIncidentManagers() {
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		WorkflowNotificationService notifications = mock(WorkflowNotificationService.class);
		when(users.findActiveUsernamesByPermissionCode("education:scheduling:manage"))
				.thenReturn(List.of("scheduler.admin", "school.admin"));
		CourseAdjustmentRecord record = new CourseAdjustmentRecord();
		record.setId("incident-1");
		record.setWorkflowInstanceId("workflow-1");
		record.setMessage("目标教室冲突");

		new CourseAdjustmentIncidentNotificationService(users, notifications)
				.enqueueFailure(record, "INITIAL");

		verify(notifications).enqueueUserEvent(
				"EDUCATION_COURSE_ADJUSTMENT_INCIDENT",
				"incident-1",
				"scheduler.admin",
				"调课回写异常待处理",
				"调课审批已完成，但课表自动回写失败。流程实例：workflow-1；原因：目标教室冲突。请进入走班排课的调课回写异常页处理。",
				"INITIAL");
		verify(notifications).enqueueUserEvent(
				"EDUCATION_COURSE_ADJUSTMENT_INCIDENT",
				"incident-1",
				"school.admin",
				"调课回写异常待处理",
				"调课审批已完成，但课表自动回写失败。流程实例：workflow-1；原因：目标教室冲突。请进入走班排课的调课回写异常页处理。",
				"INITIAL");
	}
}
