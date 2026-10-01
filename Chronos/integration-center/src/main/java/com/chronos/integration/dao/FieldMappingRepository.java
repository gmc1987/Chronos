package com.chronos.integration.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.integration.model.FieldMapping;

public interface FieldMappingRepository extends JpaRepository<FieldMapping, String> {
	List<FieldMapping> findByConnectorIdAndObjectTypeAndVersionNoOrderByTargetField(
			String connectorId, String objectType, Integer versionNo);
}
