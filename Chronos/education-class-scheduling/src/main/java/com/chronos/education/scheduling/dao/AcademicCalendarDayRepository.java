package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.AcademicCalendarDay;

public interface AcademicCalendarDayRepository extends JpaRepository<AcademicCalendarDay, String> {
	List<AcademicCalendarDay> findByAcademicTermIdOrderByCalendarDate(String academicTermId);
	Optional<AcademicCalendarDay> findByAcademicTermIdAndCalendarDate(
			String academicTermId,
			java.time.LocalDate calendarDate);
	boolean existsByAcademicTermIdAndCalendarDateAndIdNot(
			String academicTermId,
			java.time.LocalDate calendarDate,
			String id);
}
