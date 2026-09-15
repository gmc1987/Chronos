package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamInvigilationChange;

public interface ExamInvigilationChangeRepository extends JpaRepository<ExamInvigilationChange, String> {
	List<ExamInvigilationChange> findByStatusOrderByCreateTimeDesc(String status);
	boolean existsByAssignmentIdAndStatus(String assignmentId, String status);
}
