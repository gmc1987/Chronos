package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MakeupRetakeReadinessTest {

	@Test
	void remainsFailClosedUntilExamAndRetakeDependenciesExist() {
		MakeupRetakeReadiness readiness = MakeupRetakeReadiness.unavailable();

		assertFalse(readiness.available());
		assertTrue(readiness.code().equals("MAKEUP_RETAKE_UNAVAILABLE"));
		assertTrue(readiness.missingCapabilities().stream()
				.anyMatch(value -> value.startsWith("ExamScoresConfirmedV1 consumer")));
		assertThrows(IllegalStateException.class, readiness::requireAvailable);
	}
}
