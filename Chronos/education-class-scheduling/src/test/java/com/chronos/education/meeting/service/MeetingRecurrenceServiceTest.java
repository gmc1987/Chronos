package com.chronos.education.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronos.education.meeting.dao.MeetingRepository;
import com.chronos.education.meeting.dao.MeetingSeriesRepository;
import com.chronos.education.meeting.model.MeetingCommands;

@ExtendWith(MockitoExtension.class)
class MeetingRecurrenceServiceTest {
	@Mock
	private MeetingRepository meetings;
	@Mock
	private MeetingSeriesRepository series;
	@Mock
	private MeetingCenterService meetingCenter;
	@Mock
	private CalendarIntegrationService calendar;

	@Test
	void weeklyPreviewHonorsCountTermination() {
		MeetingRecurrenceService service = new MeetingRecurrenceService(
				meetings, series, meetingCenter, calendar);
		LocalDateTime start = LocalDateTime.of(2026, 10, 5, 9, 0);

		var preview = service.preview(new MeetingCommands.RecurrencePreview(
				start, start.plusHours(1), "WEEKLY", 2, "MO", null, null, 3));

		assertThat(preview.occurrences()).hasSize(3);
		assertThat(preview.occurrences().get(1).startTime())
				.isEqualTo(start.plusWeeks(2));
	}

	@Test
	void monthlyPreviewClampsDayToShortMonth() {
		MeetingRecurrenceService service = new MeetingRecurrenceService(
				meetings, series, meetingCenter, calendar);
		LocalDateTime start = LocalDateTime.of(2026, 1, 31, 9, 0);

		var preview = service.preview(new MeetingCommands.RecurrencePreview(
				start, start.plusHours(1), "MONTHLY", 1, null, 31, null, 3));

		assertThat(preview.occurrences()).extracting("startTime")
				.containsExactly(
						start,
						LocalDateTime.of(2026, 2, 28, 9, 0),
						LocalDateTime.of(2026, 3, 31, 9, 0));
	}
}
