package com.chronos.education.scheduling.model;

import java.util.List;

/** 候选快照与确认规则逐条比对，日期事项明确留在独立业务流程。 */
public record SchedulingAiRuleEvidence(
		String candidateId,
		String catalogVersion,
		int unscheduledLessons,
		String publicationBlocker,
		List<Row> rows) {
	public record Row(SchedulingAiRuleClaim rule, String status,
			int observed, int violations, String detail) {
	}
}
