package com.chronos.education.meeting.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingRecurrenceView;
import com.chronos.education.meeting.model.MeetingView;
import com.chronos.education.meeting.service.CalendarIntegrationService;
import com.chronos.education.meeting.service.MeetingRecurrenceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/education")
public class MeetingRecurrenceController {
	private final MeetingRecurrenceService recurrence;
	private final CalendarIntegrationService calendar;

	@PostMapping("/meetings/recurring/preview")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:series:view','education:meeting:series:manage','education:meeting:view','education:meeting:create','education:meeting:manage')")
	public ResultData<MeetingRecurrenceView.Preview> preview(
			@RequestBody @Valid MeetingCommands.RecurrencePreview command) {
		return ok(recurrence.preview(command));
	}

	@PostMapping("/meetings/recurring")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:series:manage','education:meeting:create','education:meeting:manage')")
	public ResultData<MeetingView> create(
			@RequestBody @Valid MeetingCommands.RecurringSave command,
			Authentication authentication) {
		return ok(recurrence.create(command, authentication.getName()));
	}

	@GetMapping("/meetings/{id}/series")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:series:view','education:meeting:series:manage','education:meeting:view','education:meeting:manage')")
	public ResultData<List<MeetingView>> instances(
			@PathVariable String id,
			Authentication authentication) {
		return ok(recurrence.instances(id, authentication.getName()));
	}

	@PostMapping("/meetings/{id}/series/change")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:series:manage','education:meeting:update','education:meeting:manage')")
	public ResultData<MeetingView> change(
			@PathVariable String id,
			@RequestBody @Valid MeetingCommands.SeriesChange command,
			Authentication authentication) {
		return ok(recurrence.change(id, command, authentication.getName()));
	}

	@PostMapping("/meetings/{id}/series/cancel")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:series:manage','education:meeting:update','education:meeting:manage')")
	public ResultData<Void> cancelSeries(
			@PathVariable String id,
			Authentication authentication) {
		recurrence.cancelSeries(id, authentication.getName());
		return ok(null);
	}

	@GetMapping("/calendar/binding")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:calendar:view','education:meeting:view','education:meeting:manage')")
	public ResultData<MeetingRecurrenceView.CalendarStatus> calendarStatus(
			Authentication authentication) {
		return ok(calendar.status(authentication.getName()));
	}

	@PostMapping("/calendar/binding")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:calendar:manage','education:meeting:update','education:meeting:manage')")
	public ResultData<MeetingRecurrenceView.CalendarStatus> saveCalendarBinding(
			@RequestBody MeetingCommands.CalendarBinding command,
			Authentication authentication) {
		return ok(calendar.saveBinding(authentication.getName(), command));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
