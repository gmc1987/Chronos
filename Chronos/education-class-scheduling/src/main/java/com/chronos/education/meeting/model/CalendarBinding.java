package com.chronos.education.meeting.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** Local binding metadata only; credentials are kept by the integration provider. */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_calendar_binding")
public class CalendarBinding extends BaseEntity {
	@Column(nullable = false, length = 128)
	private String username;

	@Column(name = "provider_code", nullable = false, length = 64)
	private String provider = "NONE";

	@Column(name = "external_calendar_id", nullable = false, length = 256)
	private String externalCalendarId;

	@Column(nullable = false, length = 24)
	private String status = "REVOKED";

	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;
}
