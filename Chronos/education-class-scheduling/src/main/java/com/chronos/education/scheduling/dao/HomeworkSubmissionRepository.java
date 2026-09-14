package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.HomeworkSubmission;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, String> {
	List<HomeworkSubmission> findByAssignmentIdOrderByCreateTimeAsc(String assignmentId);
	Optional<HomeworkSubmission> findByAssignmentIdAndStudentId(String assignmentId, String studentId);
}
