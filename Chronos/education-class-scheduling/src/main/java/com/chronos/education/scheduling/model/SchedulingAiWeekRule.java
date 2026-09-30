package com.chronos.education.scheduling.model;

/** Per-run week selection resolved to one authorized teaching offering. */
public record SchedulingAiWeekRule(
		String offeringId, String weekPattern, int startWeek, int endWeek, String sourceText) {
}
