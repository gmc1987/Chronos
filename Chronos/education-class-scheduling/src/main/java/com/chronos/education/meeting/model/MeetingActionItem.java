package com.chronos.education.meeting.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 会议行动项有独立责任人和状态，不能只隐藏在纪要正文中。 */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_action_item")
public class MeetingActionItem extends BaseEntity {
	@Column(name = "meeting_id", nullable = false, length = 64)
	private String meetingId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "assignee_username", nullable = false, length = 128)
	private String assigneeUsername;

	@Column(name = "due_at")
	private LocalDateTime dueAt;

	@Column(nullable = false, length = 24)
	private String status = "OPEN";

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;
}
