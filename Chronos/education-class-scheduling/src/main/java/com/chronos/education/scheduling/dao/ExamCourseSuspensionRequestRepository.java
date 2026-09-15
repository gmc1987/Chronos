package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamCourseSuspensionRequest;

public interface ExamCourseSuspensionRequestRepository
		extends JpaRepository<ExamCourseSuspensionRequest, String> {
	List<ExamCourseSuspensionRequest> findByPlanIdOrderByCreateTimeDesc(String planId);
	boolean existsByPlanIdAndStatus(String planId, String status);
}
