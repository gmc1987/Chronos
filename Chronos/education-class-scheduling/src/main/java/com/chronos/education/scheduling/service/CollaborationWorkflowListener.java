package com.chronos.education.scheduling.service;

import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.chronos.workflow.event.WorkflowRejectedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CollaborationWorkflowListener {
	private final CollaborationOfficeService collaboration;

	public CollaborationWorkflowListener(CollaborationOfficeService collaboration) {
		this.collaboration = collaboration;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completed(WorkflowCompletedEvent event) {
		collaboration.approveWorkflow(event.flowCode(), event.instanceId(), event.completedBy());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void rejected(WorkflowRejectedEvent event) {
		collaboration.rejectWorkflow(event.flowCode(), event.instanceId());
	}
}
