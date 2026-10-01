package com.chronos.education.scheduling.service;

import com.chronos.agent.ToolAuthorizationPolicy;
import com.chronos.agent.ToolContext;
import com.chronos.education.scheduling.dao.AgentRunRepository;
import com.chronos.security.IamAuthorization;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Rechecks the request's real principal and persisted run before a write tool. */
@Component
public class SchedulingAgentToolAuthorization implements ToolAuthorizationPolicy {
	private final AgentRunRepository runs;
	private final EducationDataScopeService scopes;
	private final IamAuthorization iam;

	public SchedulingAgentToolAuthorization(AgentRunRepository runs,
			EducationDataScopeService scopes, IamAuthorization iam) {
		this.runs = runs;
		this.scopes = scopes;
		this.iam = iam;
	}

	@Override
	public boolean permits(ToolContext context, String skillCode, String toolCode) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()
				|| !auth.getName().equals(context.actorUsername())
				|| !SchedulingAgentCapabilities.GENERATION.equals(skillCode)
				|| !SchedulingAgentCapabilities.GENERATE.equals(toolCode)
				|| !iam.has(auth, "education:ai:agent:use")
				|| !iam.has(auth, "education:scheduling:manage")
				|| !iam.has(auth, "education:scheduling:ai:use")) {
			return false;
		}
		var run = runs.findById(context.runId()).orElse(null);
		if (run == null || !auth.getName().equals(run.getOwnerUsername())
				|| !"CONFIRMED".equals(run.getStatus())
				|| run.getConfirmedPlanJson() == null
				|| run.getExpiresAt() != null && run.getExpiresAt().isBefore(LocalDateTime.now())) {
			return false;
		}
		scopes.assertFullAccess(scopes.resolve(auth.getName()));
		return true;
	}
}
