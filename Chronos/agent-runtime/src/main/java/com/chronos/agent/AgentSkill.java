package com.chronos.agent;

import java.util.Set;

/**
 * Versioned business capability exposed to the agent router.
 *
 * <p>The runtime deliberately has no dependency on a domain module. Domain
 * modules register skills and tools from their own adapter layer.</p>
 */
public interface AgentSkill {
	String code();

	int schemaVersion();

	Set<String> allowedToolCodes();

	int maxToolCalls();
}
