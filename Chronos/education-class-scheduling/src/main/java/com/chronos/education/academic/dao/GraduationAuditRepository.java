package com.chronos.education.academic.dao;

import com.chronos.education.academic.model.GraduationAudit;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GraduationAuditRepository extends JpaRepository<GraduationAudit, String> {
	List<GraduationAudit> findBySchoolIdOrderByCreateTimeDesc(String schoolId);
	boolean existsByStudentIdAndStatus(String studentId, String status);
	Optional<GraduationAudit> findFirstByStudentIdAndProgramIdAndStatusInOrderByCreateTimeDesc(
			String studentId, String programId, List<String> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select audit from GraduationAudit audit where audit.id = :id")
	Optional<GraduationAudit> findForUpdate(@Param("id") String id);
}
