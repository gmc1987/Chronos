package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface IWorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
	java.util.List<WorkflowInstance> findByInitiatorOrderByCreateTimeDesc(String initiator);
	Optional<WorkflowInstance> findByEngineInstanceId(String engineInstanceId);
	java.util.List<WorkflowInstance> findByEngineTypeAndStatus(String engineType, String status);

	/** 审批事务先锁实例，串行化同一流程的并发通过、拒绝、退回操作。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from WorkflowInstance i where i.id = :id")
	Optional<WorkflowInstance> findLockedById(String id);
}
