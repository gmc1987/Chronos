package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.DataGradeEventFact;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataGradeEventFactRepository extends JpaRepository<DataGradeEventFact, String> {
	boolean existsByEventId(String eventId);
	List<DataGradeEventFact> findByEventTypeAndAggregateIdAndOfferingId(
			String eventType, String aggregateId, String offeringId);

	long countByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
			String eventType, LocalDateTime from, LocalDateTime to);
}
