package com.chronos.education.scheduling.model;

import java.time.LocalDate;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 学期内的特殊日期；未配置的日期按照自然星期和教学周正常计算。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_academic_calendar_day",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_calendar_term_date",
				columnNames = { "academic_term_id", "calendar_date" }))
public class AcademicCalendarDay extends BaseEntity {
	@Column(name = "academic_term_id", length = 64, nullable = false)
	private String academicTermId;

	@Column(name = "calendar_date", nullable = false)
	private LocalDate calendarDate;

	@Column(name = "day_type", length = 24, nullable = false)
	private String dayType = "HOLIDAY";

	@Column(name = "day_name", length = 128, nullable = false)
	private String dayName;

	@Column(name = "teaching_day", nullable = false)
	private Boolean teachingDay = false;

	@Column(name = "remark", length = 500)
	private String remark;
}
