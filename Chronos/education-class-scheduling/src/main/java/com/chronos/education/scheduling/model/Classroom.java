package com.chronos.education.scheduling.model;

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
@Table(
		name = "edu_classroom",
		uniqueConstraints = @UniqueConstraint(name = "uk_edu_classroom_code", columnNames = "room_code"))
public class Classroom extends BaseEntity {
	@Column(name = "room_code", length = 64, nullable = false)
	private String roomCode;

	@Column(name = "room_name", length = 128, nullable = false)
	private String roomName;

	@Column(name = "campus_id", length = 64)
	private String campusId;

	@Column(name = "building_name", length = 128)
	private String buildingName;

	@Column(name = "capacity", nullable = false)
	private Integer capacity = 0;

	@Column(name = "room_type", length = 32, nullable = false)
	private String roomType = "STANDARD";

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
}
