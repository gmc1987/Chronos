package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.DataGradeEventFact;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataGradeEventFactRepository extends JpaRepository<DataGradeEventFact, String> {
	boolean existsByEventId(String eventId);

	long countByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
			String eventType, LocalDateTime from, LocalDateTime to);
}
