package com.chronos.education.grade.event;

/**
 * 工作流已经完成但成绩版本回写失败。事件只携带恢复所需标识和安全错误摘要，
 * 不携带学生成绩等敏感正文。
 */
public record GradeChangeApplyFailedEvent(
		String changeRequestId,
		String workflowInstanceId,
		String errorMessage) {
}
