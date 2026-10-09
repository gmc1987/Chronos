package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.BusinessTripConfiguration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessTripConfigurationRepository
		extends JpaRepository<BusinessTripConfiguration, String> {
	Optional<BusinessTripConfiguration> findByConfigCode(String configCode);
}
