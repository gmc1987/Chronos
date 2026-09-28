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
		List<String> unsupported,
		List<String> unresolvedClauses,
		List<SchedulingAiOfferingConstraint> offeringConstraints) {
	public SchedulingAiPlan {
		selectedOfferingIds = selectedOfferingIds == null ? Set.of() : Set.copyOf(selectedOfferingIds);
		constraints = constraints == null ? List.of() : List.copyOf(constraints);
		clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
		unsupported = unsupported == null ? List.of() : List.copyOf(unsupported);
		unresolvedClauses = unresolvedClauses == null ? List.of() : List.copyOf(unresolvedClauses);
		offeringConstraints = offeringConstraints == null ? List.of() : List.copyOf(offeringConstraints);
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported, List<String> unresolvedClauses) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds,
				candidateCount, constraints, clarifications, unsupported, unresolvedClauses, List.of());
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds, candidateCount,
				constraints, clarifications, unsupported, List.of());
	}

	public boolean readyForConfirmation() {
		return clarifications.isEmpty() && unsupported.isEmpty() && unresolvedClauses.isEmpty();
	}
}
