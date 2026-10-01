package com.chronos.education.scheduling.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class HomeworkDtos {
	private HomeworkDtos() {}

	public record AssignmentRequest(
			@NotBlank String offeringId,
			@NotBlank String type,
			String teachingPlanItemId,
			String preparationId,
			String lessonPlanId,
			@NotBlank String title,
			@NotBlank String questionSnapshotJson,
			String questionVersionRefsJson,
			String instructionsJson,
			java.time.LocalDateTime dueAt,
			java.time.LocalDateTime startAt,
			@Min(1) @Max(10000) Integer maxScore,
			@Min(1) @Max(100) Integer attemptLimit,
			boolean allowLate,
			String lateRule,
			String publishAudience,
			String attachmentSnapshotJson) {}

	public record SubmissionRequest(@NotBlank String answerSnapshotJson,
			String attachmentSnapshotJson) {}

	public record GradeRequest(@Min(0) Integer score, String teacherFeedback,
			String questionScoresJson, String annotationSnapshotJson,
			boolean returnForRevision) {}

	public record BatchGradeRequest(java.util.List<String> submissionIds,
			Integer score, String teacherFeedback, String questionScoresJson,
			String annotationSnapshotJson, boolean returnForRevision) {}
}
