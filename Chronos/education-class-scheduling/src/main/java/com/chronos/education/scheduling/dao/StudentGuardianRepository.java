package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.chronos.education.scheduling.model.StudentGuardianRelation;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardianRelation, String> {
	List<StudentGuardianRelation> findByStudentIdOrderByCreateTime(String studentId);

	List<StudentGuardianRelation> findByParentIdOrderByCreateTime(String parentId);

	Optional<StudentGuardianRelation> findByStudentIdAndParentId(String studentId, String parentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select relation from StudentGuardianRelation relation where relation.id = :id")
	Optional<StudentGuardianRelation> findForUpdate(@Param("id") String id);

	List<StudentGuardianRelation> findByStudentIdIn(List<String> studentIds);

	@Query("""
			select count(distinct relation.studentId)
			from StudentGuardianRelation relation
			where relation.studentId in :studentIds
			""")
	long countStudentsWithGuardian(@Param("studentIds") List<String> studentIds);

	long countByParentId(String parentId);

	long countByStudentId(String studentId);
}
