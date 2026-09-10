package com.chronos.education.scheduling.model;

/** 候选方案评分；硬约束未满足时以未排课课时体现，禁止应用。 */
public record ScheduleCandidateMetrics(
		int scheduledLessons,
		int unscheduledLessons,
		int preferredSlotHits,
		int sameCourseDayPenalty,
		int totalScore) {
}
