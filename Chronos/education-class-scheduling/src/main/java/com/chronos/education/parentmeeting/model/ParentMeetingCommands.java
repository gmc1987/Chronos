package com.chronos.education.parentmeeting.model;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ParentMeetingCommands {
	private ParentMeetingCommands() {}

	public record Create(
			@NotBlank @Size(max = 200) String title,
			@Size(max = 10000) String agenda,
			@NotBlank String scopeType,
			@NotBlank String scopeId,
			@NotBlank String meetingType,
			@NotNull LocalDateTime startTime,
			@NotNull LocalDateTime endTime,
			String roomId,
			String meetingProvider,
			String externalMeetingId,
			String joinUrl,
			String onlineAccessCode) {}
}
