package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.StudentGuardianRelation;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardianRelation, String> {
	List<StudentGuardianRelation> findByStudentIdOrderByCreateTime(String studentId);

	List<StudentGuardianRelation> findByParentIdOrderByCreateTime(String parentId);

	Optional<StudentGuardianRelation> findByStudentIdAndParentId(String studentId, String parentId);

	long countByParentId(String parentId);

	long countByStudentId(String studentId);
}
