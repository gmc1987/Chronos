package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IWorkflowOutboxRepository extends JpaRepository<WorkflowOutbox, String> {
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
}
