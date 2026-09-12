package com.chronos.education.scheduling.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ScheduleDateException;

public interface ScheduleDateExceptionRepository extends JpaRepository<ScheduleDateException, String> {
	List<ScheduleDateException> findBySemesterCodeAndStatusOrderBySourceDateAsc(
			String semesterCode,
			String status);
	List<ScheduleDateException> findBySemesterCodeOrderBySourceDateDesc(String semesterCode);
	List<ScheduleDateException> findBySemesterCodeAndSourceDateBetweenAndStatusOrderBySourceDateAsc(
			String semesterCode,
			LocalDate startDate,
			LocalDate endDate,
			String status);

	Optional<ScheduleDateException> findByWorkflowInstanceId(String workflowInstanceId);
}
