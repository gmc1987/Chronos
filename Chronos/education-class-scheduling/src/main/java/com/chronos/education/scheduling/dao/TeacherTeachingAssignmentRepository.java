package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.TeacherTeachingAssignment;

public interface TeacherTeachingAssignmentRepository
		extends JpaRepository<TeacherTeachingAssignment, String> {
	List<TeacherTeachingAssignment> findAllByOrderByCreateTimeDesc();

	long countByTeacherId(String teacherId);

	long countBySubjectId(String subjectId);

	long countByGradeId(String gradeId);
}
