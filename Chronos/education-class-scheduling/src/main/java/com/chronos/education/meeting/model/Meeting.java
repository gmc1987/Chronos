package com.chronos.education.meeting.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 会议与会议室预约合并提交，但会议本身允许不占用实体会议室。 */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting")
public class Meeting extends BaseEntity {
	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "text")
	private String agenda;

	@Column(name = "meeting_type", nullable = false, length = 16)
	private String meetingType;

	@Column(name = "start_time", nullable = false)
	private LocalDateTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalDateTime endTime;

	@Column(name = "organizer_username", nullable = false, length = 128)
	private String organizerUsername;

	@Column(name = "room_id", length = 64)
	private String roomId;

	@Column(name = "meeting_provider", length = 32)
	private String meetingProvider;

	@Column(name = "external_meeting_id", length = 128)
	private String externalMeetingId;

	@Column(name = "join_url", length = 1000)
	private String joinUrl;

	@Column(name = "online_access_code", length = 128)
	private String onlineAccessCode;

	@Column(nullable = false, length = 24)
	private String status = "DRAFT";

	@Column(name = "room_decision_by", length = 128)
	private String roomDecisionBy;

	@Column(name = "room_decision_at")
	private LocalDateTime roomDecisionAt;

	@Column(name = "room_decision_comment", length = 1000)
	private String roomDecisionComment;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "cancel_reason", length = 1000)
	private String cancelReason;

	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;
}
