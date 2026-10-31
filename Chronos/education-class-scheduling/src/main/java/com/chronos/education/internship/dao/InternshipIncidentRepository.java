package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipIncident;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternshipIncidentRepository extends JpaRepository<InternshipIncident, String> {
	List<InternshipIncident> findByPlacementIdOrderByOccurredAtDesc(String placementId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select value from InternshipIncident value where value.id = :id")
	Optional<InternshipIncident> findForUpdate(@Param("id") String id);
}
