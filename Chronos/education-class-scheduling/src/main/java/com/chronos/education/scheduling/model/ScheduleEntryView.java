package com.chronos.education.scheduling.model;

public record ScheduleEntryView(
		String id,
		String semesterCode,
		String offeringId,
		String offeringCode,
		String courseName,
		String teachingClassName,
		String teacherName,
		String classroomId,
		String classroomName,
		Integer dayOfWeek,
		Integer periodNo,
		Integer durationPeriods,
		String weekPattern,
		Integer startWeek,
		Integer endWeek,
		String status,
		Boolean locked) {
}
