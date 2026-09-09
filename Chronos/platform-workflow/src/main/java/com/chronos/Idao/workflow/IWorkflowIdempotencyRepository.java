package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowIdempotency;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowIdempotencyRepository extends JpaRepository<WorkflowIdempotency, String> {
	Optional<WorkflowIdempotency> findByActorAndOperationAndIdempotencyKey(String actor, String operation,
			String idempotencyKey);

	void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
