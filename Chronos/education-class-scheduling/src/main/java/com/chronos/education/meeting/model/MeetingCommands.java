package com.chronos.education.meeting.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MeetingCommands {
	private MeetingCommands() {
	}

	public record Room(
			String roomCode,
			String roomName,
			String campusId,
			String buildingName,
			String location,
			Integer capacity,
			String equipmentJson,
			String approvalMode,
			String approverUsername,
			Boolean enabled,
			Long recordVersion) {
	}

	public record Save(
			@NotBlank @Size(max = 200) String title,
			@Size(max = 10000) String agenda,
			@NotBlank @Size(max = 16) String meetingType,
			@NotNull LocalDateTime startTime,
			@NotNull LocalDateTime endTime,
			String roomId,
			@Size(max = 32) String meetingProvider,
			@Size(max = 128) String externalMeetingId,
			@Size(max = 1000) String joinUrl,
			@Size(max = 128) String onlineAccessCode,
			@Size(max = 500) List<@NotBlank @Size(max = 128) String> participantUsernames,
			Long recordVersion) {
	}

	public record Decision(boolean approve, String comment) {
	}

	public record Response(String status, String comment) {
	}

	public record Cancellation(String reason) {
	}

	public record ParticipantOption(String username, String displayName) {
	}
}
