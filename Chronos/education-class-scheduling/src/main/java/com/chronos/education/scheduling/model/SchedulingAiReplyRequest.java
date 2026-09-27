package com.chronos.education.scheduling.model;

public record SchedulingAiReplyRequest(String answer, Integer expectedPlanVersion) {
	public SchedulingAiReplyRequest {
		if (answer == null || answer.isBlank() || answer.trim().length() > 2000) {
			throw new IllegalArgumentException("answer 不能为空且长度不能超过 2000");
		}
		if (expectedPlanVersion == null || expectedPlanVersion < 1) {
			throw new IllegalArgumentException("expectedPlanVersion 必须为正数");
		}
		answer = answer.trim();
	}
}
