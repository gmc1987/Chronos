package com.chronos.education.scheduling.model;

public record SchedulingAiConstraint(
		String kind,
		String strength,
		String teacherId,
		String teacherName,
		Integer dayOfWeek,
		Integer periodNo,
		String timePhrase,
		String sourceText,
		String resolution) {
}
