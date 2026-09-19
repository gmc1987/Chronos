package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.DataEventConsumption;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataEventConsumptionRepository extends JpaRepository<DataEventConsumption, String> {
	Optional<DataEventConsumption> findByEventId(String eventId);
}
