package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教室维修、考试占用或临时停用形成的周期性不可排课时段。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_classroom_unavailable_slot")
public class ClassroomUnavailableSlot extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;
	@Column(name = "classroom_id", length = 64, nullable = false)
	private String classroomId;
	@Column(name = "day_of_week", nullable = false)
	private Integer dayOfWeek;
	@Column(name = "start_period", nullable = false)
	private Integer startPeriod;
	@Column(name = "end_period", nullable = false)
	private Integer endPeriod;
	@Column(name = "reason", length = 500, nullable = false)
	private String reason;
	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
