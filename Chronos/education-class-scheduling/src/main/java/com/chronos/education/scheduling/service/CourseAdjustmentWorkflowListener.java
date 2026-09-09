package com.chronos.education.scheduling.service;

import com.chronos.workflow.event.WorkflowCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 接收调课审批完成事件；业务事务与失败记录事务由应用服务分别控制。 */
@Component
public class CourseAdjustmentWorkflowListener {
	private static final String FLOW_CODE = "EDU_COURSE_ADJUSTMENT_APPROVAL";

	private final CourseAdjustmentApplicationService applicationService;

	public CourseAdjustmentWorkflowListener(CourseAdjustmentApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onWorkflowCompleted(WorkflowCompletedEvent event) {
		if (!FLOW_CODE.equals(event.flowCode())) {
			return;
		}
		try {
			applicationService.apply(event);
		} catch (RuntimeException exception) {
			// 审批已提交，回写异常不能再反向抛给 Flowable，必须转为可观察、可重放的事故。
			applicationService.recordFailure(event, exception);
		}
	}
}
