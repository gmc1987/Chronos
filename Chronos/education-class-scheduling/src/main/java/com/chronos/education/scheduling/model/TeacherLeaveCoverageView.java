package com.chronos.education.scheduling.model;

import java.time.LocalDate;

/** 已批准教师请假所影响、尚待教务安排代课的具体课次。 */
public record TeacherLeaveCoverageView(
		String leaveId,
		String teacherId,
		LocalDate date,
		ScheduleOccurrenceView occurrence) {
}
