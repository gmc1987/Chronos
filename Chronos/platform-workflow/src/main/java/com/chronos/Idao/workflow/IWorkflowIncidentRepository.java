package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowIncident;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface IWorkflowIncidentRepository extends JpaRepository<WorkflowIncident, String> {
	List<WorkflowIncident> findByStatusOrderByCreateTimeDesc(String status);

	List<WorkflowIncident> findAllByOrderByCreateTimeDesc();

	Optional<WorkflowIncident> findByEngineJobId(String engineJobId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select incident from WorkflowIncident incident where incident.id = :id")
	Optional<WorkflowIncident> findLockedById(String id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select incident from WorkflowIncident incident where incident.engineJobId = :engineJobId")
	Optional<WorkflowIncident> findLockedByEngineJobId(String engineJobId);

	List<WorkflowIncident> findByStatusAndNextRetryAtBeforeOrderByCreateTimeAsc(String status, LocalDateTime now);
}
