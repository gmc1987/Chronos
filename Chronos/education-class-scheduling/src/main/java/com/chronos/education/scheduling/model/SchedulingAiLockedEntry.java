package com.chronos.education.scheduling.model;

/** Existing timetable entry and slot temporarily preserved by one AI run. */
public record SchedulingAiLockedEntry(
		String entryId, String offeringId, int dayOfWeek, int periodNo, String sourceText) {
}
