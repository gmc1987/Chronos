package com.chronos.education.scheduling.model;

import java.util.List;

/** 已确认日期规则对应的真实业务事实，供用户核对并进入现有审批流程。 */
public record SchedulingAiDateImpact(
		List<SchedulingAiDateRule> rules,
		List<AcademicCalendarDay> calendarDays,
		List<PendingMakeupView> pendingMakeups,
		List<TeacherLeaveCoverageView> pendingSubstitutions,
		List<ExamPlanImpact> examPlans) {
	public record ExamPlanImpact(String planId, String planName, String status,
			int affectedLessonCount, String issue) { }
}
