package com.chronos.education.grade.dto;

import java.math.BigDecimal;

public final class GradeProductionDtos {
	private GradeProductionDtos() {
	}

	public record ChangeRequestCommand(String courseGradeId, BigDecimal afterScore, String reason) {
	}

	public record ChangeDecisionCommand(String taskId, boolean approved, String comment) {
	}

	public record MakeupRegisterCommand(String sourceGradeId, String attemptType, String remark) {
	}

	public record MakeupResultCommand(BigDecimal resultScore, String remark) {
	}

	public record PublishedGradeView(
			String id,
			String studentId,
			String studentNo,
			String studentName,
			BigDecimal totalScore,
			Boolean passed,
			Integer versionNo) {
	}
}
