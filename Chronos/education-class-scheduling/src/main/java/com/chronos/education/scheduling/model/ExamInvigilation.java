package com.chronos.education.scheduling.model;

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
@Table(name = "edu_exam_invigilation")
public class ExamInvigilation extends BaseEntity {
	@Column(name = "room_id", nullable = false, length = 64)
	private String roomId;

	@Column(name = "teacher_id", nullable = false, length = 64)
	private String teacherId;

	@Column(name = "duty_role", nullable = false, length = 24)
	private String dutyRole;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "ASSIGNED";

	@Column(name = "acknowledged_at")
	private LocalDateTime acknowledgedAt;

	@Column(name = "checked_in_at")
	private LocalDateTime checkedInAt;

	@Column(name = "absent_at")
	private LocalDateTime absentAt;

	@Column(name = "absence_escalated_at")
	private LocalDateTime absenceEscalatedAt;

	@Column(name = "replaced_by_id", length = 64)
	private String replacedById;
}
