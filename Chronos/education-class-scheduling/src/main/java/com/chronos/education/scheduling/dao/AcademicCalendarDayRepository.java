package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.AcademicCalendarDay;

public interface AcademicCalendarDayRepository extends JpaRepository<AcademicCalendarDay, String> {
	List<AcademicCalendarDay> findByAcademicTermIdOrderByCalendarDate(String academicTermId);
	boolean existsByAcademicTermIdAndCalendarDateAndIdNot(
			String academicTermId,
			java.time.LocalDate calendarDate,
			String id);
}
