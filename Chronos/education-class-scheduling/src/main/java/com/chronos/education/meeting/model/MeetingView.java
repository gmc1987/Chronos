package com.chronos.education.meeting.model;

import java.util.List;

public record MeetingView(
		Meeting meeting,
		MeetingRoom room,
		List<MeetingParticipant> participants,
		MeetingParticipant currentParticipant,
		String currentUsername,
		List<MeetingMaterial> materials,
		MeetingMinutes minutes,
		List<MeetingActionItem> actionItems) {
}
