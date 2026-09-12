package com.chronos.education.scheduling.model;

public record ScheduleEntryCommand(
		String semesterCode,
		String offeringId,
		String classroomId,
		Integer dayOfWeek,
		Integer periodNo,
		Integer durationPeriods,
		String weekPattern,
		Integer startWeek,
		Integer endWeek,
		Boolean locked,
		Long recordVersion) {
}
