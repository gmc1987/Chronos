package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.CombinedOfferingSourceClass;

public interface CombinedOfferingSourceClassRepository
		extends JpaRepository<CombinedOfferingSourceClass, String> {
	List<CombinedOfferingSourceClass> findByOfferingIdOrderByAdministrativeClassId(String offeringId);

	void deleteByOfferingId(String offeringId);
}
