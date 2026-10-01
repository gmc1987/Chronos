package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.GradeChangeIncident;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeChangeIncidentRepository extends JpaRepository<GradeChangeIncident, String> {
	Optional<GradeChangeIncident> findByChangeRequestId(String changeRequestId);

	Page<GradeChangeIncident> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select incident from GradeChangeIncident incident where incident.id = :id")
	Optional<GradeChangeIncident> findByIdForUpdate(@Param("id") String id);
}
