package com.chronos.education.scheduling.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class HomeworkDtos {
	private HomeworkDtos() {}

	public record AssignmentRequest(
			@NotBlank String offeringId,
			String teachingPlanItemId,
			String preparationId,
			String lessonPlanId,
			@NotBlank String title,
			@NotBlank String questionSnapshotJson,
			String instructionsJson,
			java.time.LocalDateTime dueAt,
			@Min(1) @Max(10000) Integer maxScore,
			boolean allowLate) {}

	public record SubmissionRequest(@NotBlank String answerSnapshotJson) {}

	public record GradeRequest(@Min(0) Integer score, String teacherFeedback,
			boolean returnForRevision) {}
}
