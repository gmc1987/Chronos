package com.chronos.education.meeting.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** Rule metadata for a series; individual occurrences remain edu_meeting rows. */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_series")
public class MeetingSeries extends BaseEntity {
	@Column(name = "organizer_username", nullable = false, length = 128)
	private String organizerUsername;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 64)
	private String timezone = "UTC";

	@Column(name = "recurrence_rule", nullable = false, length = 500)
	private String recurrenceRule;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Column(name = "until_at")
	private LocalDateTime untilAt;

	@Column(name = "occurrence_limit")
	private Integer occurrenceLimit;

	@Column(nullable = false, length = 24)
	private String status = "DRAFT";

	@Version
	@Column(name = "version_no", nullable = false)
	private Long versionNo = 0L;
}
