package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.DataEventConsumption;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataEventConsumptionRepository extends JpaRepository<DataEventConsumption, String> {
	Optional<DataEventConsumption> findByEventId(String eventId);

	@Modifying
	@Query(value = """
			insert into data_event_consumption
				(id, create_by, create_time, event_id, event_type, aggregate_id, payload_json,
				 status, attempts, next_attempt_at)
			values (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, :eventId, :eventType,
					:aggregateId, :payloadJson, 'PENDING', 0, :nextAttemptAt)
			on conflict (event_id) do nothing
			""", nativeQuery = true)
	int claimIfAbsent(
			@Param("eventId") String eventId,
			@Param("eventType") String eventType,
			@Param("aggregateId") String aggregateId,
			@Param("payloadJson") String payloadJson,
			@Param("nextAttemptAt") LocalDateTime nextAttemptAt);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select consumption from DataEventConsumption consumption where consumption.eventId = :eventId")
	Optional<DataEventConsumption> findByEventIdForUpdate(@Param("eventId") String eventId);
}
