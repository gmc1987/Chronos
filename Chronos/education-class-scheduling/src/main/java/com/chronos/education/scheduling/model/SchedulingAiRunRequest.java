package com.chronos.education.scheduling.model;

import java.util.Set;

/** Strict first-turn input; IDs are validated against authoritative services. */
public record SchedulingAiRunRequest(
		String clientRequestId,
		String semesterCode,
		String mode,
		Set<String> selectedOfferingIds,
		Integer candidateCount,
		String requestText,
		Integer weekdays,
		Integer periodsPerDay,
		Integer startWeek,
		Integer endWeek) {
	public SchedulingAiRunRequest {
		clientRequestId = required(clientRequestId, "clientRequestId", 128);
		semesterCode = required(semesterCode, "semesterCode", 32);
		requestText = required(requestText, "requestText", 2000);
		mode = mode == null || mode.isBlank() ? "GLOBAL" : mode.trim().toUpperCase();
		if (!Set.of("GLOBAL", "LOCAL").contains(mode)) {
			throw new IllegalArgumentException("mode 仅支持 GLOBAL 或 LOCAL");
		}
		selectedOfferingIds = selectedOfferingIds == null ? Set.of() : Set.copyOf(selectedOfferingIds);
		if ("LOCAL".equals(mode) && selectedOfferingIds.isEmpty()) {
			throw new IllegalArgumentException("LOCAL 模式必须提供 selectedOfferingIds");
		}
		candidateCount = candidateCount == null ? 3 : candidateCount;
		if (candidateCount < 1 || candidateCount > 5) {
			throw new IllegalArgumentException("candidateCount 必须在 1 到 5 之间");
		}
		if (weekdays != null && (weekdays < 1 || weekdays > 7)
				|| periodsPerDay != null && (periodsPerDay < 1 || periodsPerDay > 20)
				|| startWeek != null && (startWeek < 1 || startWeek > 52)
				|| endWeek != null && (endWeek < 1 || endWeek > 52)
				|| startWeek != null && endWeek != null && endWeek < startWeek) {
			throw new IllegalArgumentException("AI 排课时间范围无效");
		}
	}

	public SchedulingAiRunRequest(String clientRequestId, String semesterCode, String mode,
			Set<String> selectedOfferingIds, Integer candidateCount, String requestText) {
		this(clientRequestId, semesterCode, mode, selectedOfferingIds, candidateCount,
				requestText, null, null, null, null);
	}

	private static String required(String value, String label, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "不能为空");
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "长度不能超过 " + maxLength);
		}
		return normalized;
	}
}
