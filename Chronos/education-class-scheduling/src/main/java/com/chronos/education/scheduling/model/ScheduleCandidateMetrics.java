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
		int consecutiveBlockHits,
		int teacherDayConcentrationHits,
		java.util.List<SlotRuleCheck> slotRuleChecks) {
	public ScheduleCandidateMetrics {
		slotRuleChecks = slotRuleChecks == null ? java.util.List.of() : java.util.List.copyOf(slotRuleChecks);
	}

	public ScheduleCandidateMetrics(int scheduledLessons, int unscheduledLessons,
			int preferredSlotHits, int sameCourseDayPenalty, int teacherLoadPenalty,
			int consecutivePenalty, int campusSwitchPenalty, int teacherGapPenalty,
			int totalScore, int consecutiveBlockHits, int teacherDayConcentrationHits) {
		this(scheduledLessons, unscheduledLessons, preferredSlotHits, sameCourseDayPenalty,
				teacherLoadPenalty, consecutivePenalty, campusSwitchPenalty, teacherGapPenalty,
				totalScore, consecutiveBlockHits, teacherDayConcentrationHits, java.util.List.of());
	}

	public record SlotRuleCheck(String targetType, String targetId, int dayOfWeek,
			int periodNo, int violations) {
	}

	public ScheduleCandidateMetrics(int scheduledLessons, int unscheduledLessons,
			int preferredSlotHits, int sameCourseDayPenalty, int teacherLoadPenalty,
			int consecutivePenalty, int campusSwitchPenalty, int teacherGapPenalty,
			int totalScore, int consecutiveBlockHits) {
		this(scheduledLessons, unscheduledLessons, preferredSlotHits, sameCourseDayPenalty,
				teacherLoadPenalty, consecutivePenalty, campusSwitchPenalty, teacherGapPenalty,
				totalScore, consecutiveBlockHits, 0);
	}

	public ScheduleCandidateMetrics(int scheduledLessons, int unscheduledLessons,
			int preferredSlotHits, int sameCourseDayPenalty, int teacherLoadPenalty,
			int consecutivePenalty, int campusSwitchPenalty, int teacherGapPenalty,
			int totalScore) {
		this(scheduledLessons, unscheduledLessons, preferredSlotHits, sameCourseDayPenalty,
				teacherLoadPenalty, consecutivePenalty, campusSwitchPenalty, teacherGapPenalty,
				totalScore, 0, 0);
	}
}
