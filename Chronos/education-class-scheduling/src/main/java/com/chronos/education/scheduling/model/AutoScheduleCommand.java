package com.chronos.education.scheduling.model;

import java.util.Set;

/** 自动排课请求；LOCAL 模式只重排 selectedOfferingIds 中的教学任务。 */
public record AutoScheduleCommand(
		String semesterCode,
		String planName,
		String mode,
		Set<String> selectedOfferingIds,
		Integer candidateCount,
		Integer weekdays,
		Integer periodsPerDay,
		Integer startWeek,
		Integer endWeek) {
}
