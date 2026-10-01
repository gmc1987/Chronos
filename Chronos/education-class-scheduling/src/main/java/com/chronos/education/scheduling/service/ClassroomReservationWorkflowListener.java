package com.chronos.education.scheduling.service;

import com.chronos.workflow.event.WorkflowCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 审批提交后将最终表单快照转换为正式教室占用。 */
@Component
public class ClassroomReservationWorkflowListener {
	private final ClassroomReservationService service;

	public ClassroomReservationWorkflowListener(ClassroomReservationService service) {
		this.service = service;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onWorkflowCompleted(WorkflowCompletedEvent event) {
		if (!ClassroomReservationStartValidator.FLOW_CODE.equals(event.flowCode())) {
			return;
		}
		try {
			service.apply(event);
		} catch (RuntimeException exception) {
			// 工作流已完成，资源回写失败必须沉淀为可查询、可重试的业务事故。
			service.recordFailure(event, exception);
		}
	}
}
