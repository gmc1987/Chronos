package com.chronos.education.meeting.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** 可预约的实体会议室；审批模式决定空闲时自动确认还是转管理员审核。 */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_room")
public class MeetingRoom extends BaseEntity {
	@Column(name = "room_code", nullable = false, length = 64, unique = true)
	private String roomCode;

	@Column(name = "room_name", nullable = false, length = 128)
	private String roomName;

	@Column(name = "campus_id", length = 64)
	private String campusId;

	@Column(name = "building_name", length = 128)
	private String buildingName;

	@Column(name = "location", length = 255)
	private String location;

	@Column(nullable = false)
	private Integer capacity;

	@Column(name = "equipment_json", columnDefinition = "text")
	private String equipmentJson;

	@Column(name = "approval_mode", nullable = false, length = 16)
	private String approvalMode = "AUTO";

	@Column(name = "approver_username", length = 128)
	private String approverUsername;

	@Column(nullable = false)
	private Boolean enabled = true;

	@Version
	@Column(name = "record_version", nullable = false)
	private Long recordVersion = 0L;
}
