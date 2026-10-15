package com.chronos.education.scheduling.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.chronos.education.scheduling.model.HomeworkSubmission;

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

	/** 学生只能读取已经正式发布的分数和批语，批改草稿始终留在教师端。 */
	public record StudentSubmissionView(String id, String assignmentId, String status,
			Integer attemptNo, String answerSnapshotJson, String attachmentSnapshotJson,
			Integer score, String teacherFeedback, String questionScoresJson,
			boolean gradesPublished, java.time.LocalDateTime submittedAt) {
		public static StudentSubmissionView from(HomeworkSubmission submission) {
			if (submission == null) return null;
			boolean published = submission.isGradesPublished();
			return new StudentSubmissionView(submission.getId(), submission.getAssignmentId(),
					submission.getStatus(), submission.getAttemptNo(), submission.getAnswerSnapshotJson(),
					submission.getAttachmentSnapshotJson(), published ? submission.getScore() : null,
					published ? submission.getTeacherFeedback() : null,
					published ? submission.getQuestionScoresJson() : null,
					published, submission.getSubmittedAt());
		}
	}

	public record GradeRequest(@Min(0) Integer score, String teacherFeedback,
			String questionScoresJson, String annotationSnapshotJson,
			boolean returnForRevision) {}

	public record BatchGradeRequest(java.util.List<String> submissionIds,
			Integer score, String teacherFeedback, String questionScoresJson,
			String annotationSnapshotJson, boolean returnForRevision) {}
}
