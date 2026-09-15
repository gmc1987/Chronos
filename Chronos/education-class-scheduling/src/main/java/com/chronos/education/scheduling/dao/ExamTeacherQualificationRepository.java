package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamTeacherQualification;

public interface ExamTeacherQualificationRepository
		extends JpaRepository<ExamTeacherQualification, String> {
	List<ExamTeacherQualification> findByTeacherId(String teacherId);
	Optional<ExamTeacherQualification> findByTeacherIdAndSubjectId(
			String teacherId,
			String subjectId);
	boolean existsByTeacherIdAndSubjectId(String teacherId, String subjectId);
}
