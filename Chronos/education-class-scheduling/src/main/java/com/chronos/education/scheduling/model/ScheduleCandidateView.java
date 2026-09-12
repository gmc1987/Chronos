package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

public record ScheduleCandidateView(
		String id,
		String semesterCode,
		String planName,
		String generationMode,
		Integer entryCount,
		Integer unscheduledLessons,
		Integer totalScore,
		String status,
		String reviewStatus,
		String ownerUsername,
		String collaborationRemark,
		String reviewedBy,
		LocalDateTime reviewedAt,
		String reviewComment,
		String generatedBy,
		LocalDateTime generatedAt,
		String appliedBy,
		LocalDateTime appliedAt,
		ScheduleCandidateMetrics metrics) {
}
