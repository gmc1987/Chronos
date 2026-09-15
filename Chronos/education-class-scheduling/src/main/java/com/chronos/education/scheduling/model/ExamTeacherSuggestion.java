package com.chronos.education.scheduling.model;

/** 排班候选只暴露教务员需要的身份与工作量，不返回完整教师档案。 */
public record ExamTeacherSuggestion(
		String id,
		String teacherName,
		long semesterDutyCount) {
}
