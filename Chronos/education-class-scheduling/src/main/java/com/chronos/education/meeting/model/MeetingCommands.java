package com.chronos.education.meeting.model;

import java.time.LocalDateTime;
import java.util.List;

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
			String title,
			String agenda,
			String meetingType,
			LocalDateTime startTime,
			LocalDateTime endTime,
			String roomId,
			String meetingProvider,
			String externalMeetingId,
			String joinUrl,
			String onlineAccessCode,
			List<String> participantUsernames,
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
