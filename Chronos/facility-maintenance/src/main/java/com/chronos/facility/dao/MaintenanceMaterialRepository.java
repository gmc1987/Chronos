package com.chronos.facility.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.chronos.facility.model.MaintenanceMaterial;

public interface MaintenanceMaterialRepository
		extends JpaRepository<MaintenanceMaterial, String>, JpaSpecificationExecutor<MaintenanceMaterial> {
	Optional<MaintenanceMaterial> findByMaterialCode(String materialCode);
}
