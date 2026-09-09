package com.chronos.Idao.workflow;

import com.chronos.model.workflow.WorkflowNotification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowNotificationRepository extends JpaRepository<WorkflowNotification, String> {
	List<WorkflowNotification> findTop100ByRecipientOrderByCreateTimeDesc(String recipient);
	Page<WorkflowNotification> findByRecipientOrderByCreateTimeDesc(String recipient, Pageable pageable);
	long countByRecipientAndReadAtIsNull(String recipient);
	List<WorkflowNotification> findByRecipientAndReadAtIsNull(String recipient);
	boolean existsBySourceEventId(String sourceEventId);
}
