package com.chronos.agent;

/**
 * Server-owned authorization hook for write tools. Implementations must verify
 * the actor's permission and the persisted run state and plan for this tool
 * (READY_FOR_CONFIRMATION is permitted only for DRAFT_WRITE; CONFIRMED_WRITE
 * requires a confirmed plan). Model output and caller-supplied status alone
 * are not authorization.
 * With no implementation registered, all write tools are denied.
 */
@FunctionalInterface
public interface ToolAuthorizationPolicy {
	boolean permits(ToolContext context, String skillCode, String toolCode);
}
