package com.chronos.education.parentmeeting.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_parent_meeting_scope", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_parent_meeting_scope", columnNames = { "meeting_id", "scope_type", "scope_id" }))
public class ParentMeetingScope extends BaseEntity {
	@Column(name = "meeting_id", nullable = false, length = 64)
	private String meetingId;
	@Column(name = "scope_type", nullable = false, length = 16)
	private String scopeType;
	@Column(name = "scope_id", nullable = false, length = 64)
	private String scopeId;
}
