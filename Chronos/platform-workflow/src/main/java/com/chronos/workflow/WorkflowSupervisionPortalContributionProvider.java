package com.chronos.workflow;

import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/** 将用户发起且仍在运行的流程作为可催办事项提供给门户。 */
@Component
public class WorkflowSupervisionPortalContributionProvider implements PortalContributionProvider {
	private final WorkflowService workflows;

	public WorkflowSupervisionPortalContributionProvider(WorkflowService workflows) {
		this.workflows = workflows;
	}

	@Override
	public String providerCode() {
		return "SUPERVISION";
	}

	@Override
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:instance:view')")
	public PortalContribution load(String username) {
		return new PortalContribution(
				providerCode(),
				true,
				"ok",
				workflows.portalSupervision(username));
	}
}
