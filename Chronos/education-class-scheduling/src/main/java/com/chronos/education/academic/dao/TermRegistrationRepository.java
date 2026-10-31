package com.chronos.education.academic.dao;

import com.chronos.education.academic.model.TermRegistration;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermRegistrationRepository extends JpaRepository<TermRegistration, String> {
	List<TermRegistration> findBySchoolIdAndTermCodeOrderByCreateTimeDesc(String schoolId, String termCode);
	Optional<TermRegistration> findBySchoolIdAndTermCodeAndStudentId(String schoolId, String termCode, String studentId);
	boolean existsByTermCodeAndStudentIdAndStatus(String termCode, String studentId, String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select registration from TermRegistration registration where registration.id = :id")
	Optional<TermRegistration> findForUpdate(@Param("id") String id);
}
