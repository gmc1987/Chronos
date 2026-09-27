package com.chronos.education.meeting.model;

import java.time.LocalDateTime;
import java.util.List;

public final class MeetingRecurrenceView {
	private MeetingRecurrenceView() {
	}

	public record Occurrence(
			String occurrenceKey,
			LocalDateTime startTime,
			LocalDateTime endTime,
			int ordinal) {
	}

	public record Preview(
			String frequency,
			int interval,
			String byDay,
			Integer dayOfMonth,
			LocalDateTime until,
			Integer count,
			List<Occurrence> occurrences) {
	}

	public record CalendarStatus(
			boolean configured,
			String provider,
			String message,
			String externalCalendarId) {
	}
}
