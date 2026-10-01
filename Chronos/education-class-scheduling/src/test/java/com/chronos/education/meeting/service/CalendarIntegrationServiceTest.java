package com.chronos.education.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronos.education.meeting.dao.CalendarBindingRepository;
import com.chronos.education.meeting.dao.CalendarOutboxRepository;
import com.chronos.education.meeting.model.CalendarOutbox;
import com.chronos.education.meeting.model.Meeting;

@ExtendWith(MockitoExtension.class)
class CalendarIntegrationServiceTest {
	@Mock
	private CalendarBindingRepository bindings;
	@Mock
	private CalendarOutboxRepository outbox;

	@Test
	void enqueueReturnsExistingEventForSameIdempotencyKey() {
		CalendarIntegrationService service = new CalendarIntegrationService(bindings, outbox);
		Meeting meeting = new Meeting();
		meeting.setId("meeting-1");
		meeting.setOrganizerUsername("teacher-1");
		meeting.setOccurrenceKey("2026-10-05T09:00");
		CalendarOutbox existing = new CalendarOutbox();
		existing.setIdempotencyKey("meeting-1:2026-10-05T09:00:UPSERT");
		when(outbox.findByIdempotencyKey(existing.getIdempotencyKey()))
				.thenReturn(Optional.of(existing));

		assertThat(service.enqueue(meeting, "upsert")).isSameAs(existing);
	}
}
