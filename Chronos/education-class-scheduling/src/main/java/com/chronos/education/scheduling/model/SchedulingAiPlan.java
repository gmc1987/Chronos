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
		List<SchedulingAiOfferingConstraint> offeringConstraints,
		List<SchedulingAiWeekRule> weekRules,
		List<SchedulingAiLockedEntry> lockedEntries,
		List<SchedulingAiSoftPriority> softPriorities,
		List<SchedulingAiSlotRule> slotRules,
		SchedulingAiGenerationOptions generationOptions,
		List<SchedulingAiDateRule> dateRules) {
	public SchedulingAiPlan {
		selectedOfferingIds = selectedOfferingIds == null ? Set.of() : Set.copyOf(selectedOfferingIds);
		constraints = constraints == null ? List.of() : List.copyOf(constraints);
		clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
		unsupported = unsupported == null ? List.of() : List.copyOf(unsupported);
		unresolvedClauses = unresolvedClauses == null ? List.of() : List.copyOf(unresolvedClauses);
		offeringConstraints = offeringConstraints == null ? List.of() : List.copyOf(offeringConstraints);
		weekRules = weekRules == null ? List.of() : List.copyOf(weekRules);
		lockedEntries = lockedEntries == null ? List.of() : List.copyOf(lockedEntries);
		softPriorities = softPriorities == null ? List.of() : List.copyOf(softPriorities);
		slotRules = slotRules == null ? List.of() : List.copyOf(slotRules);
		dateRules = dateRules == null ? List.of() : List.copyOf(dateRules);
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported, List<String> unresolvedClauses,
			List<SchedulingAiOfferingConstraint> offeringConstraints,
			List<SchedulingAiWeekRule> weekRules,
			List<SchedulingAiLockedEntry> lockedEntries,
			List<SchedulingAiSoftPriority> softPriorities,
			List<SchedulingAiSlotRule> slotRules,
			SchedulingAiGenerationOptions generationOptions) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds,
				candidateCount, constraints, clarifications, unsupported, unresolvedClauses,
				offeringConstraints, weekRules, lockedEntries, softPriorities, slotRules,
				generationOptions, List.of());
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported, List<String> unresolvedClauses,
			List<SchedulingAiOfferingConstraint> offeringConstraints,
			List<SchedulingAiWeekRule> weekRules,
			List<SchedulingAiLockedEntry> lockedEntries,
			List<SchedulingAiSoftPriority> softPriorities,
			List<SchedulingAiSlotRule> slotRules) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds,
				candidateCount, constraints, clarifications, unsupported, unresolvedClauses,
				offeringConstraints, weekRules, lockedEntries, softPriorities, slotRules, null);
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported, List<String> unresolvedClauses,
			List<SchedulingAiOfferingConstraint> offeringConstraints,
			List<SchedulingAiWeekRule> weekRules,
			List<SchedulingAiLockedEntry> lockedEntries,
			List<SchedulingAiSoftPriority> softPriorities) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds, candidateCount,
				constraints, clarifications, unsupported, unresolvedClauses,
				offeringConstraints, weekRules, lockedEntries, softPriorities, List.of());
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported, List<String> unresolvedClauses,
			List<SchedulingAiOfferingConstraint> offeringConstraints) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds, candidateCount,
				constraints, clarifications, unsupported, unresolvedClauses,
				offeringConstraints, List.of(), List.of(), List.of());
	}

	public SchedulingAiPlan(int schemaVersion, String skillCode, String semesterCode,
			String mode, Set<String> selectedOfferingIds, int candidateCount,
			List<SchedulingAiConstraint> constraints, List<String> clarifications,
			List<String> unsupported, List<String> unresolvedClauses) {
		this(schemaVersion, skillCode, semesterCode, mode, selectedOfferingIds,
				candidateCount, constraints, clarifications, unsupported, unresolvedClauses,
				List.of(), List.of(), List.of(), List.of());
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
