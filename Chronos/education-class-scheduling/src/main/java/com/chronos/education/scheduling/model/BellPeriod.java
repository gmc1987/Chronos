package com.chronos.education.scheduling.model;

import java.time.LocalTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 作息方案内可用于排课的一个节次。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_bell_period",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_bell_period_no",
				columnNames = { "bell_schedule_id", "period_no" }))
public class BellPeriod extends BaseEntity {
	@Column(name = "bell_schedule_id", length = 64, nullable = false)
	private String bellScheduleId;

	@Column(name = "period_no", nullable = false)
	private Integer periodNo;

	@Column(name = "period_name", length = 64, nullable = false)
	private String periodName;

	@Column(name = "day_segment", length = 24, nullable = false)
	private String daySegment;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "schedulable", nullable = false)
	private Boolean schedulable = true;
}
