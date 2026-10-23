package com.chronos.education.operationagent.service;

import java.util.List;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.operationagent.model.OperationAgentRun;
import com.chronos.education.operationagent.model.OperationAgentStepRun;
import com.chronos.workflow.WorkflowNotificationService;

/** 将审批、失败和完成状态推送到现有可靠通知出箱。 */
@Service
public class OperationAgentNotificationService {
	public static final String REVIEW_PERMISSION = "education:operation-agent:review";
	private final IAdminUserRepository users;
	private final WorkflowNotificationService notifications;

	public OperationAgentNotificationService(
			IAdminUserRepository users,
			WorkflowNotificationService notifications) {
		this.users = users;
		this.notifications = notifications;
	}

	public void approvalRequired(OperationAgentRun run, OperationAgentStepRun step) {
		var recipients = new LinkedHashSet<>(eligibleReviewers(run.getOwnerUsername()));
		notifications.enqueueUserEvents(
				"EDUCATION_OPERATION_AGENT_APPROVAL",
				run.getId(), recipients,
				"学期运营智能体步骤待审核",
				"计划运行到“" + step.getStepName() + "”，请由非发起人审核后继续。",
				"STEP:" + step.getStepOrder() + ":ATTEMPT:" + step.getAttemptNo());
	}

	/** Returns the active reviewers that can approve this owner's checkpoint. */
	public List<String> eligibleReviewers(String ownerUsername) {
		var recipients = new LinkedHashSet<>(users.findActiveUsernamesByPermissionCode(REVIEW_PERMISSION));
		recipients.remove(ownerUsername);
		return List.copyOf(recipients);
	}

	public void failed(OperationAgentRun run) {
		notifications.enqueueUserEvent(
				"EDUCATION_OPERATION_AGENT_FAILED", run.getId(), run.getOwnerUsername(),
				"学期运营智能体执行失败", run.getErrorMessage(),
				"FAILED:" + run.getConditionVersion());
	}

	public void completed(OperationAgentRun run) {
		notifications.enqueueUserEvent(
				"EDUCATION_OPERATION_AGENT_COMPLETED", run.getId(), run.getOwnerUsername(),
				"学期运营智能体执行完成", run.getSummary(),
				"COMPLETED:" + run.getConditionVersion());
	}
}
