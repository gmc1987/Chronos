package com.chronos.education.scheduling.model;

import java.util.List;
import java.util.Set;

public record SchedulingAiPlan(
		int schemaVersion,
		String skillCode,
		String semesterCode,
		String mode,
		Set<String> selectedOfferingIds,
		int candidateCount,
		List<SchedulingAiConstraint> constraints,
		List<String> clarifications,
		List<String> unsupported) {
	public SchedulingAiPlan {
		selectedOfferingIds = selectedOfferingIds == null ? Set.of() : Set.copyOf(selectedOfferingIds);
		constraints = constraints == null ? List.of() : List.copyOf(constraints);
		clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
		unsupported = unsupported == null ? List.of() : List.copyOf(unsupported);
	}

	public boolean readyForConfirmation() {
		return clarifications.isEmpty() && unsupported.isEmpty();
	}
}
