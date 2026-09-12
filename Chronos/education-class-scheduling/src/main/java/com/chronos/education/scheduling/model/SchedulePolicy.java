package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按学期保存自动排课评分规则和发布质量门禁，避免生产规则散落为代码常量。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_schedule_policy",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_schedule_policy_semester",
				columnNames = "semester_code"))
public class SchedulePolicy extends BaseEntity {
	@Column(name = "semester_code", length = 64, nullable = false)
	private String semesterCode;

	@Column(name = "default_max_weekly_lessons", nullable = false)
	private Integer defaultMaxWeeklyLessons = 20;

	@Column(name = "default_max_daily_lessons", nullable = false)
	private Integer defaultMaxDailyLessons = 6;

	@Column(name = "default_max_consecutive_lessons", nullable = false)
	private Integer defaultMaxConsecutiveLessons = 4;

	@Column(name = "scheduled_lesson_reward", nullable = false)
	private Integer scheduledLessonReward = 100;

	@Column(name = "preferred_slot_reward", nullable = false)
	private Integer preferredSlotReward = 10;

	@Column(name = "same_course_day_penalty", nullable = false)
	private Integer sameCourseDayPenalty = 5;

	@Column(name = "teacher_load_penalty", nullable = false)
	private Integer teacherLoadPenalty = 2;

	@Column(name = "consecutive_penalty", nullable = false)
	private Integer consecutivePenalty = 4;

	@Column(name = "campus_switch_penalty", nullable = false)
	private Integer campusSwitchPenalty = 25;

	@Column(name = "teacher_gap_penalty", nullable = false)
	private Integer teacherGapPenalty = 3;

	@Column(name = "minimum_campus_travel_periods", nullable = false)
	private Integer minimumCampusTravelPeriods = 1;

	@Column(name = "unscheduled_lesson_penalty", nullable = false)
	private Integer unscheduledLessonPenalty = 1000;

	@Column(name = "course_concentration_threshold", nullable = false)
	private Integer courseConcentrationThreshold = 2;

	@Column(name = "block_teacher_overload", nullable = false)
	private Boolean blockTeacherOverload = true;

	@Column(name = "block_hard_conflicts", nullable = false)
	private Boolean blockHardConflicts = true;

	@Column(name = "block_incomplete_offerings", nullable = false)
	private Boolean blockIncompleteOfferings = true;

	@Version
	@Column(name = "version_no", nullable = false)
	private Long versionNo = 0L;
}
