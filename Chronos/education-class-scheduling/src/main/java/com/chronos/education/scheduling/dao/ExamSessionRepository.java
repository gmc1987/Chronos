package com.chronos.education.scheduling.dao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamSession;

public interface ExamSessionRepository extends JpaRepository<ExamSession, String> {
	List<ExamSession> findByPlanIdOrderByExamDateAscStartTimeAsc(String planId);
	List<ExamSession> findByExamDateBetweenAndStatus(
			LocalDate startDate,
			LocalDate endDate,
			String status);

	List<ExamSession> findByExamDateAndStartTimeLessThanAndEndTimeGreaterThan(
			LocalDate examDate,
			LocalTime endTime,
			LocalTime startTime);
}
