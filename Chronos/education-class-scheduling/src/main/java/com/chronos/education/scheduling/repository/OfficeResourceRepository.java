package com.chronos.education.scheduling.repository;

import com.chronos.education.scheduling.model.OfficeResource;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficeResourceRepository extends JpaRepository<OfficeResource, String> {
	List<OfficeResource> findByResourceTypeAndEnabledTrueOrderByResourceNameAsc(String resourceType);
	List<OfficeResource> findByResourceTypeOrderByResourceNameAsc(String resourceType);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select resource from OfficeResource resource where resource.id = :id")
	Optional<OfficeResource> findLockedById(@Param("id") String id);
}
