package com.chronos.workflow;

import com.chronos.file.service.ManagedFileAccessPolicy;
import org.springframework.stereotype.Component;

/** 流程参与人可以读取已绑定到该流程实例的表单附件。 */
@Component
public class WorkflowManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	private final WorkflowSecurityService workflowSecurity;

	public WorkflowManagedFileAccessPolicy(WorkflowSecurityService workflowSecurity) {
		this.workflowSecurity = workflowSecurity;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		return "WORKFLOW_FORM".equals(businessType)
				&& businessId != null
				&& workflowSecurity.canViewInstance(username, businessId);
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		return "WORKFLOW_FORM".equals(businessType)
				&& businessId != null
				&& workflowSecurity.canEditAnyFileField(username, businessId);
	}

	@Override
	public boolean canWrite(
			String username,
			String businessType,
			String businessId,
			String fileId) {
		return "WORKFLOW_FORM".equals(businessType)
				&& businessId != null
				&& fileId != null
				&& workflowSecurity.canEditFile(username, businessId, fileId);
	}
}
