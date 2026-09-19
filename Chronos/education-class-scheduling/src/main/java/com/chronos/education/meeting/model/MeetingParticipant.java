package com.chronos.education.meeting.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "edu_meeting_participant")
public class MeetingParticipant extends BaseEntity {
	@Column(name = "meeting_id", nullable = false, length = 64)
	private String meetingId;

	@Column(nullable = false, length = 128)
	private String username;

	@Column(name = "participant_role", nullable = false, length = 24)
	private String participantRole = "ATTENDEE";

	@Column(name = "response_status", nullable = false, length = 24)
	private String responseStatus = "INVITED";

	@Column(name = "response_comment", length = 500)
	private String responseComment;

	@Column(name = "responded_at")
	private LocalDateTime respondedAt;

	@Column(name = "checked_in_at")
	private LocalDateTime checkedInAt;

	@Column(name = "check_in_method", length = 24)
	private String checkInMethod;
}
