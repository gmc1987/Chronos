package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipAssessment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternshipAssessmentRepository extends JpaRepository<InternshipAssessment, String> {
	Optional<InternshipAssessment> findByPlacementId(String placementId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select value from InternshipAssessment value where value.id = :id")
	Optional<InternshipAssessment> findForUpdate(@Param("id") String id);
}
