package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamPublishedChange;

public interface ExamPublishedChangeRepository
		extends JpaRepository<ExamPublishedChange, String> {
	List<ExamPublishedChange> findByPlanIdOrderByCreateTimeDesc(String planId);
	boolean existsByPlanIdAndStatus(String planId, String status);
}
