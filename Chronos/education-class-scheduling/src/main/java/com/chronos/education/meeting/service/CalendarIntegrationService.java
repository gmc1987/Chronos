package com.chronos.education.meeting.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.meeting.dao.CalendarBindingRepository;
import com.chronos.education.meeting.dao.CalendarOutboxRepository;
import com.chronos.education.meeting.model.CalendarBinding;
import com.chronos.education.meeting.model.CalendarOutbox;
import com.chronos.education.meeting.model.Meeting;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingRecurrenceView;

import lombok.RequiredArgsConstructor;

/**
 * Stores local calendar intent. Provider credentials belong to integration-center
 * and are deliberately not accepted or returned by this module.
 */
@Service
@RequiredArgsConstructor
public class CalendarIntegrationService {
	private final CalendarBindingRepository bindings;
	private final CalendarOutboxRepository outbox;

	@Transactional(readOnly = true)
	public MeetingRecurrenceView.CalendarStatus status(String username) {
		return bindings.findByUsername(username)
				.map(binding -> status(binding))
				.orElse(new MeetingRecurrenceView.CalendarStatus(
						false, "NONE", "未接入", null));
	}

	@Transactional
	public MeetingRecurrenceView.CalendarStatus saveBinding(
			String username,
			MeetingCommands.CalendarBinding command) {
		String provider = normalize(command == null ? null : command.provider());
		boolean enabled = command != null && Boolean.TRUE.equals(command.enabled());
		if (enabled && ("NONE".equals(provider) || provider.isBlank())) {
			throw new IllegalArgumentException("未配置外部日历 provider，会议仍可使用本地日历");
		}
		String externalCalendarId = trim(command == null
				? null : command.externalCalendarId());
		if (externalCalendarId == null) {
			throw new IllegalArgumentException("外部日历未接入，不能保存空的 calendar id");
		}
		CalendarBinding binding = bindings.findByUsername(username)
				.orElseGet(CalendarBinding::new);
		binding.setUsername(username);
		binding.setProvider(provider.isBlank() ? "NONE" : provider);
		binding.setExternalCalendarId(externalCalendarId);
		binding.setStatus(enabled ? "ACTIVE" : "REVOKED");
		binding = bindings.save(binding);
		return status(binding);
	}

	/**
	 * The deterministic key makes retries and repeated series edits harmless.
	 * A database unique constraint remains the final race-condition guard.
	 */
	@Transactional
	public CalendarOutbox enqueue(Meeting meeting, String operation) {
		String occurrenceKey = meeting.getOccurrenceKey() == null
				? meeting.getId() : meeting.getOccurrenceKey();
		String key = meeting.getId() + ":" + occurrenceKey + ":"
				+ operation.toUpperCase(Locale.ROOT);
		return outbox.findByIdempotencyKey(key).orElseGet(() -> {
			CalendarOutbox event = new CalendarOutbox();
			event.setIdempotencyKey(key);
			event.setMeetingId(meeting.getId());
			event.setOccurrenceKey(occurrenceKey);
			event.setOperation(operation.toUpperCase(Locale.ROOT));
			event.setParticipantUsername(meeting.getOrganizerUsername());
			event.setStatus("PENDING");
			event.setAttemptCount(0);
			return outbox.save(event);
		});
	}

	private MeetingRecurrenceView.CalendarStatus status(CalendarBinding binding) {
		boolean configured = "ACTIVE".equalsIgnoreCase(binding.getStatus())
				&& !"NONE".equalsIgnoreCase(binding.getProvider())
				&& binding.getExternalCalendarId() != null;
		return new MeetingRecurrenceView.CalendarStatus(
				configured,
				binding.getProvider(),
				configured ? "已配置" : "未接入",
				binding.getExternalCalendarId());
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
