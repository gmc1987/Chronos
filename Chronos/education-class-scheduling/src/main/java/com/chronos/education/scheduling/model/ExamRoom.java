package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "edu_exam_room")
public class ExamRoom extends BaseEntity {
	@Column(name = "session_id", nullable = false, length = 64)
	private String sessionId;

	@Column(name = "classroom_id", nullable = false, length = 64)
	private String classroomId;

	@Column(name = "required_invigilators", nullable = false)
	private Integer requiredInvigilators = 2;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "DRAFT";
}
