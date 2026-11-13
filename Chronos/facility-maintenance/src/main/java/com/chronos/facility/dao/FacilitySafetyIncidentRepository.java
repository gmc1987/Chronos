package com.chronos.facility.dao;

import com.chronos.facility.model.FacilitySafetyIncident;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacilitySafetyIncidentRepository extends JpaRepository<FacilitySafetyIncident, String> {
	@Query("select i from FacilitySafetyIncident i where i.organizationId in :ids order by i.occurredAt desc")
	List<FacilitySafetyIncident> findScopedIncidents(@Param("ids") java.util.Collection<String> ids);

	List<FacilitySafetyIncident> findAllByOrderByOccurredAtDesc();
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select incident from FacilitySafetyIncident incident where incident.id = :id")
	Optional<FacilitySafetyIncident> findForUpdate(@Param("id") String id);
}
