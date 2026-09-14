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
@Table(name = "edu_exam_candidate")
public class ExamCandidate extends BaseEntity {
	@Column(name = "room_id", nullable = false, length = 64)
	private String roomId;

	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;

	@Column(name = "seat_no")
	private Integer seatNo;
}
