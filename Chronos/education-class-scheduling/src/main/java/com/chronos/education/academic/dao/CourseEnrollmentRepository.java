package com.chronos.education.academic.dao;

import com.chronos.education.academic.model.CourseEnrollment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, String> {
	List<CourseEnrollment> findBySchoolIdAndTermCodeOrderByCreateTimeDesc(String schoolId, String termCode);
	List<CourseEnrollment> findByTermCodeAndStudentIdAndStatus(String termCode, String studentId, String status);
	Optional<CourseEnrollment> findByTermCodeAndStudentIdAndOfferingId(String termCode, String studentId, String offeringId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select enrollment from CourseEnrollment enrollment where enrollment.id = :id")
	Optional<CourseEnrollment> findForUpdate(@Param("id") String id);
}
