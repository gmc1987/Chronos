package com.chronos.education.scheduling.model;

/** 候选方案评分；硬约束未满足时以未排课课时体现，禁止应用。 */
public record ScheduleCandidateMetrics(
		int scheduledLessons,
		int unscheduledLessons,
		int preferredSlotHits,
		int sameCourseDayPenalty,
		int teacherLoadPenalty,
		int consecutivePenalty,
		int campusSwitchPenalty,
		int teacherGapPenalty,
		int totalScore,
		int consecutiveBlockHits) {
	public ScheduleCandidateMetrics(int scheduledLessons, int unscheduledLessons,
			int preferredSlotHits, int sameCourseDayPenalty, int teacherLoadPenalty,
			int consecutivePenalty, int campusSwitchPenalty, int teacherGapPenalty,
			int totalScore) {
		this(scheduledLessons, unscheduledLessons, preferredSlotHits, sameCourseDayPenalty,
				teacherLoadPenalty, consecutivePenalty, campusSwitchPenalty, teacherGapPenalty,
				totalScore, 0);
	}
}
