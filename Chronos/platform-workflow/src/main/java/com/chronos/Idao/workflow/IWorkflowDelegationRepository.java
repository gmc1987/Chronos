package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowDelegation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowDelegationRepository extends JpaRepository<WorkflowDelegation, String> {
	List<WorkflowDelegation> findByDelegatorAndEnabledTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
			String delegator, LocalDateTime startAt, LocalDateTime endAt);

	List<WorkflowDelegation> findByDelegatorOrderByCreateTimeDesc(String delegator);
	List<WorkflowDelegation> findByDelegateeOrderByCreateTimeDesc(String delegatee);
	Page<WorkflowDelegation> findByDelegatorOrderByCreateTimeDesc(String delegator, Pageable pageable);
	Page<WorkflowDelegation> findByDelegateeOrderByCreateTimeDesc(String delegatee, Pageable pageable);
}
