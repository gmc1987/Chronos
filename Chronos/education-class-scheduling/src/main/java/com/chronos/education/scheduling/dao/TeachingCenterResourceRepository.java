package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.TeachingCenterResource;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingCenterResourceRepository
		extends JpaRepository<TeachingCenterResource, String> {
	Page<TeachingCenterResource> findByResourceTypeAndOfferingIdAndArchivedFalse(
			String resourceType, String offeringId, Pageable pageable);
	Page<TeachingCenterResource> findByResourceTypeAndArchivedFalse(
			String resourceType, Pageable pageable);
	List<TeachingCenterResource> findByOfferingIdAndArchivedFalse(String offeringId);
}
