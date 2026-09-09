package com.chronos.workflow;

import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/** 向统一门户首页提供当前用户的流程待办摘要。 */
@Component
public class WorkflowPortalContributionProvider implements PortalContributionProvider {
	private final WorkflowService workflows;

	public WorkflowPortalContributionProvider(WorkflowService workflows) {
		this.workflows = workflows;
	}

	@Override
	public String providerCode() {
		return "WORKFLOW";
	}

	@Override
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public PortalContribution load(String username) {
		return new PortalContribution(
				providerCode(),
				true,
				"ok",
				workflows.portalTodo(username));
	}
}
