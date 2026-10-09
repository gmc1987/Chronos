package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface IWorkflowOutboxRepository extends JpaRepository<WorkflowOutbox, String> {
	long countByStatus(String status);

	List<WorkflowOutbox> findTop100ByStatusAndNextAttemptAtBeforeOrderByCreateTimeAsc(String status,
			LocalDateTime nextAttemptAt);

	/** 多实例调度器通过跳过已锁行来分片领取事件，避免重复投递。 */
	@Query(value = """
			select *
			from wf_outbox
			where status = 'PENDING'
			  and next_attempt_at <= :now
			order by create_time
			limit 100
			for update skip locked
			""", nativeQuery = true)
	List<WorkflowOutbox> lockDispatchBatch(@Param("now") LocalDateTime now);

	List<WorkflowOutbox> findTop100ByStatusOrderByCreateTimeDesc(String status);
	Page<WorkflowOutbox> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);
	boolean existsByDeduplicationKey(String deduplicationKey);

	/** 批量入箱与调用方业务事务一起提交，唯一键在并发重试时保证幂等。 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			insert into wf_outbox (
			    id, create_by, create_time, event_type, aggregate_id,
			    deduplication_key, payload_json, status, attempts, next_attempt_at
			)
			select gen_random_uuid()::text, 'SYSTEM', current_timestamp,
			       :eventType, :aggregateId, item.deduplication_key,
			       item.payload_json, 'PENDING', 0, current_timestamp
			from jsonb_to_recordset(cast(:eventsJson as jsonb))
			     as item(deduplication_key text, payload_json text)
			on conflict (deduplication_key) do nothing
			""", nativeQuery = true)
	int insertUserEventBatch(
			@Param("eventType") String eventType,
			@Param("aggregateId") String aggregateId,
			@Param("eventsJson") String eventsJson);
}
