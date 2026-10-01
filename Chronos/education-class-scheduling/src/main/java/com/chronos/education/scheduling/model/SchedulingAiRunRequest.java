package com.chronos.education.scheduling.model;

import java.util.Set;

/** Strict first-turn input; IDs are validated against authoritative services. */
public record SchedulingAiRunRequest(
		String clientRequestId,
		String semesterCode,
		String mode,
		Set<String> selectedOfferingIds,
		Integer candidateCount,
		String requestText) {
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
