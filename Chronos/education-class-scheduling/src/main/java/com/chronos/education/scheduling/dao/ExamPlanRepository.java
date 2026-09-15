package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamPlan;

public interface ExamPlanRepository extends JpaRepository<ExamPlan, String> {
	List<ExamPlan> findBySemesterCodeOrderByStartDateAsc(String semesterCode);
}
