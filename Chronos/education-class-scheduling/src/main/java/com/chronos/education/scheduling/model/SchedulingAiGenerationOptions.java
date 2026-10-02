package com.chronos.education.scheduling.model;

/** User-confirmed generation scope, shared with the ordinary scheduling command. */
public record SchedulingAiGenerationOptions(int weekdays, int periodsPerDay,
		int startWeek, int endWeek) {
	public SchedulingAiGenerationOptions {
		if (weekdays < 1 || weekdays > 7 || periodsPerDay < 1 || periodsPerDay > 20
				|| startWeek < 1 || endWeek > 52 || endWeek < startWeek) {
			throw new IllegalArgumentException("AI 排课时间范围无效");
		}
	}
}
