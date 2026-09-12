package com.chronos.education.scheduling.model;

import java.time.LocalDate;

public record ScheduleOccurrenceView(
		String occurrenceKey,
		LocalDate date,
		ScheduleEntryView entry,
		String occurrenceStatus,
		String exceptionType,
		String exceptionId,
		String reason,
		Integer effectivePeriodNo,
		String effectiveClassroomId,
		String substituteTeacherId) {
}
