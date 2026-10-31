package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipPlacement;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternshipPlacementRepository extends JpaRepository<InternshipPlacement, String> {
	List<InternshipPlacement> findBySchoolIdAndTermCodeOrderByCreateTimeDesc(String schoolId, String termCode);
	long countByPositionIdAndStatusIn(String positionId, List<String> statuses);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select value from InternshipPlacement value where value.id = :id")
	Optional<InternshipPlacement> findForUpdate(@Param("id") String id);
}
