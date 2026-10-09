package com.chronos.education.scheduling.service;

import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.chronos.workflow.event.WorkflowRejectedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 出差审批结果写回业务台账并触发财务预算动作。 */
@Component
public class BusinessTripWorkflowListener {
	private final BusinessTripService trips;

	public BusinessTripWorkflowListener(BusinessTripService trips) {
		this.trips = trips;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completed(WorkflowCompletedEvent event) {
		if (BusinessTripService.FLOW_CODE.equals(event.flowCode())) {
			trips.approve(
					event.instanceId(),
					event.businessKey(),
					event.initiatedBy(),
					event.completedBy(),
					event.mainFormData());
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void rejected(WorkflowRejectedEvent event) {
		if (BusinessTripService.FLOW_CODE.equals(event.flowCode())) {
			trips.reject(event.instanceId());
		}
	}
}
