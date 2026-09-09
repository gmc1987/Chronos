package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.TeacherTeachingAssignment;

public interface TeacherTeachingAssignmentRepository
		extends JpaRepository<TeacherTeachingAssignment, String> {
	List<TeacherTeachingAssignment> findAllByOrderByCreateTimeDesc();
	Page<TeacherTeachingAssignment> findAllByOrderByCreateTimeDesc(Pageable pageable);

	long countByTeacherId(String teacherId);

	long countBySubjectId(String subjectId);

	long countByGradeId(String gradeId);
}
