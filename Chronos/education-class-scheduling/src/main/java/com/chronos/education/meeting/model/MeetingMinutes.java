package com.chronos.education.meeting.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 会议纪要发布后冻结，继续修改必须先回到草稿并产生新的审计记录。 */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_minutes")
public class MeetingMinutes extends BaseEntity {
	@Column(name = "meeting_id", nullable = false, length = 64, unique = true)
	private String meetingId;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "decisions_text", columnDefinition = "text")
	private String decisionsText;

	@Column(nullable = false, length = 24)
	private String status = "DRAFT";

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;
}
