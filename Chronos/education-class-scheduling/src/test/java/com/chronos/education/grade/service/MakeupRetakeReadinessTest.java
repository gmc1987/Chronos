package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MakeupRetakeReadinessTest {

	@Test
	void remainsFailClosedUntilExamAndRetakeDependenciesExist() {
		MakeupRetakeReadiness readiness = MakeupRetakeReadiness.unavailable();

		assertFalse(readiness.available());
		assertTrue(readiness.reasonCode().equals(MakeupRetakeReadiness.UNAVAILABLE_REASON));
		assertTrue(readiness.missingCapabilities().stream()
				.anyMatch(value -> value.startsWith("ExamScoresConfirmedV1 consumer")));
		assertTrue(readiness.requiredAuthorities().contains("education:score:makeup-retake:manage"));
		assertFalse(readiness.studentAndGuardianVisible());
		assertThrows(IllegalStateException.class, readiness::requireAvailable);
		assertThrows(IllegalStateException.class,
				() -> readiness.requireManagePermission(Set.of("education:score:makeup-retake:manage")));
	}

	@Test
	void unavailableCapabilityDoesNotGrantPermissionToAnyCaller() {
		MakeupRetakeReadiness readiness = MakeupRetakeReadiness.unavailable();

		assertThrows(IllegalStateException.class, () -> readiness.requireManagePermission(Set.of()));
	}
}
