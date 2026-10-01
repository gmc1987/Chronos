package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import java.util.List;

public record SchedulingAiRunView(
		String id,
		String clientRequestId,
		String status,
		Integer planVersion,
		SchedulingAiPlan plan,
		String relatedJobId,
		String errorCode,
		String errorMessage,
		LocalDateTime expiresAt,
		List<AgentStep> steps) {
}
