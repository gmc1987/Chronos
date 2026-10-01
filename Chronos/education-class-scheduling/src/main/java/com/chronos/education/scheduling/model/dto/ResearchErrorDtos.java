package com.chronos.education.scheduling.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public final class ResearchErrorDtos {
	private ResearchErrorDtos() {}
	public record GroupRequest(@NotBlank String name, String subjectId, String campusId,
			@NotBlank String leaderTeacherId, String courseScopeJson, String description) {}
	public record MemberRequest(@NotBlank String teacherId, String role) {}
	public record ActivityRequest(@NotBlank String title, LocalDateTime activityTime,
			LocalDateTime endTime, String location, String agenda, String courseId, String topicId) {
		public ActivityRequest(String title, LocalDateTime activityTime, LocalDateTime endTime,
				String location, String agenda) {
			this(title, activityTime, endTime, location, agenda, null, null);
		}
	}
	public record ActivityCancelRequest(@NotBlank String reason) {}
	public record AttendanceRequest(@NotBlank String teacherId, @NotBlank String status, String leaveReason) {}
	public record MaterialRequest(@NotBlank String title, @NotBlank String fileId) {}
	public record ResultRequest(@NotBlank String title, @NotBlank String resultType,
			String content, String fileId) {}
	public record ErrorManualRequest(@NotBlank String studentId, String courseId, String semesterId,
			String questionId, String knowledgePointId, @NotBlank String errorReason, String sourceRef,
			String analysis, String studentNote, String sourceType, String sourceItemId, String questionVersionId,
			LocalDateTime occurredAt) {
		public ErrorManualRequest(String studentId, String courseId, String semesterId, String questionId,
				String knowledgePointId, String errorReason, String sourceRef, String analysis, String studentNote) {
			this(studentId, courseId, semesterId, questionId, knowledgePointId, errorReason, sourceRef,
					analysis, studentNote, "MANUAL", null, null, null);
		}
	}
	public record WrongAnswerConfirmed(@NotBlank String eventId, @NotBlank String studentId,
			String courseId, String semesterId, String questionId, @NotBlank String sourceItemId,
			String sourceRef, String analysis, String sourceType, String questionVersionId,
			LocalDateTime occurredAt) {
		public WrongAnswerConfirmed(String eventId, String studentId, String courseId, String semesterId,
				String questionId, String sourceItemId, String sourceRef, String analysis) {
			this(eventId, studentId, courseId, semesterId, questionId, sourceItemId, sourceRef, analysis,
					"HOMEWORK", null, null);
		}
	}
	public record TeacherErrorAggregate(String questionId, String knowledgePointId, long studentCount,
			long errorCount, LocalDateTime lastWrongAt) {}
	public record MasteryRequest(@NotBlank String status, String note) {}
	public record ErrorReviewRequest(@NotBlank String status, String note) {}
	public record MinutesRequest(@NotBlank String minutes) {}
	public record ResultTransitionRequest(@NotBlank String status) {}
}
