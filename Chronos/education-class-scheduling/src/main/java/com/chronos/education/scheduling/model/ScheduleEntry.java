package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 周课表项；周次区间支持分阶段开课，星期和节次共同构成时间槽。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_schedule_entry",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_schedule_offering_slot",
				columnNames = { "offering_id", "day_of_week", "period_no", "start_week", "end_week" }))
public class ScheduleEntry extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "offering_id", length = 64, nullable = false)
	private String offeringId;

	@Column(name = "classroom_id", length = 64, nullable = false)
	private String classroomId;

	@Column(name = "day_of_week", nullable = false)
	private Integer dayOfWeek;

	@Column(name = "period_no", nullable = false)
	private Integer periodNo;

	@Column(name = "duration_periods", nullable = false, columnDefinition = "integer default 1")
	private Integer durationPeriods = 1;

	@Column(name = "week_pattern", length = 16, nullable = false, columnDefinition = "varchar(16) default 'ALL'")
	private String weekPattern = "ALL";

	@Column(name = "start_week", nullable = false)
	private Integer startWeek = 1;

	@Column(name = "end_week", nullable = false)
	private Integer endWeek = 20;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "SCHEDULED";

	@Column(name = "substitute_teacher_id", length = 64)
	private String substituteTeacherId;

	@Column(name = "source_adjustment_instance_id", length = 64)
	private String sourceAdjustmentInstanceId;

	@Column(name = "locked", nullable = false, columnDefinition = "boolean default false")
	private Boolean locked = false;
}
