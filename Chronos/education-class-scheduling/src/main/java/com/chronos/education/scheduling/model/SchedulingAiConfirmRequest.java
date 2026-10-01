package com.chronos.education.scheduling.model;

public record SchedulingAiConfirmRequest(Integer expectedPlanVersion) {
	public SchedulingAiConfirmRequest {
		if (expectedPlanVersion == null || expectedPlanVersion < 1) {
			throw new IllegalArgumentException("expectedPlanVersion 必须为正数");
		}
	}
}
