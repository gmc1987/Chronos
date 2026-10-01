package com.chronos.education.scheduling.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SchedulingAiRunRequestTest {
	@Test
	void requiresSemesterAndRejectsUnsupportedMode() {
		assertThatThrownBy(() -> new SchedulingAiRunRequest(
				"request-1", "", "GLOBAL", Set.of(), 1, "排课"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("semesterCode");
		assertThatThrownBy(() -> new SchedulingAiRunRequest(
				"request-1", "2026-2027-1", "AUTO", Set.of(), 1, "排课"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("mode");
	}

	@Test
	void localModeRequiresSelectedOfferingsAndBoundsCandidates() {
		assertThatThrownBy(() -> new SchedulingAiRunRequest(
				"request-1", "2026-2027-1", "LOCAL", Set.of(), 1, "局部排课"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("selectedOfferingIds");
		assertThatThrownBy(() -> new SchedulingAiRunRequest(
				"request-1", "2026-2027-1", "GLOBAL", Set.of(), 6, "排课"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("candidateCount");
	}
}
