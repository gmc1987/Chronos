package com.chronos.education.scheduling.model;

import java.util.List;

public record SchedulingCandidateExplanation(String candidateId, List<Fact> facts) {
	public record Fact(String code, int value, String text) {
	}
}
