package com.chronos.education.scheduling.model;

/** Grounded, run-local prohibition; one entry per target and time slot. */
public record SchedulingAiSlotRule(String targetType, String targetId, String targetName,
		int dayOfWeek, int periodNo, String sourceText) {
}
