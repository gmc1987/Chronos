package com.chronos.workflow;

import java.util.List;

/** Deployment-level access to a workflow definition, independent of its user ACL. */
@FunctionalInterface
public interface WorkflowProductAccess {
	boolean allows(String flowCode);

	default boolean canConfigureTemplate(String flowCode, String actor) {
		return false;
	}

	default boolean canStart(String flowCode, String actor) {
		return true;
	}

	default boolean startsInLauncher(String flowCode) {
		return true;
	}

	default boolean canViewInstance(String flowCode, String initiator, String actor) {
		return true;
	}

	default boolean canHandle(String flowCode, String initiator, String actor) {
		return true;
	}

	default boolean canDecide(String flowCode, String initiator, String reviewer) {
		return true;
	}

	default boolean canAssignApproval(String definitionId, String initiator, String reviewer) {
		return true;
	}

	/** Null keeps the configured role. An empty list means no eligible reviewer. */
	default List<String> defaultReviewers(String definitionId, String initiator, String configuredRole) {
		return null;
	}
}
