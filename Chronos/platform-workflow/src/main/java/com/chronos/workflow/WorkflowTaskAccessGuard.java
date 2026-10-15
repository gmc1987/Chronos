package com.chronos.workflow;

/**
 * Domain boundary for workflow tasks. A role may be a candidate for a node
 * while its business record still belongs to another school or organization.
 */
public interface WorkflowTaskAccessGuard {
	boolean supports(String flowCode);

	/** Read-only list filtering must not throw across a transactional service boundary. */
	default boolean canAccess(String businessKey, String actor) {
		requireAccess(businessKey, actor);
		return true;
	}

	void requireAccess(String businessKey, String actor);
}
