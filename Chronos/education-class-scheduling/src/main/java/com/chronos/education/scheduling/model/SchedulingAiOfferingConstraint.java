package com.chronos.education.scheduling.model;

/** Course-level consecutive-period preference resolved to an actual offering. */
public record SchedulingAiOfferingConstraint(
		String offeringId, String courseName, int periods, String sourceText) {
}
