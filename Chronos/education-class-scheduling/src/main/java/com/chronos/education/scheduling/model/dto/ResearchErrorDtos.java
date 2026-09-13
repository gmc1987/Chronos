package com.chronos.education.scheduling.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public final class ResearchErrorDtos {
	private ResearchErrorDtos() {}
	public record GroupRequest(@NotBlank String name, String subjectId, String campusId,
			@NotBlank String leaderTeacherId, String courseScopeJson, String description) {}
	public record MemberRequest(@NotBlank String teacherId, String role) {}
	public record ActivityRequest(@NotBlank String title, LocalDateTime activityTime,
			LocalDateTime endTime, String location, String agenda) {}
	public record AttendanceRequest(@NotBlank String teacherId, @NotBlank String status, String leaveReason) {}
	public record MaterialRequest(@NotBlank String title, @NotBlank String fileId) {}
	public record ResultRequest(@NotBlank String title, @NotBlank String resultType,
			String content, String fileId) {}
	public record ErrorManualRequest(@NotBlank String studentId, String courseId, String semesterId,
			String questionId, String sourceRef, String analysis, String studentNote) {}
	public record WrongAnswerConfirmed(@NotBlank String eventId, @NotBlank String studentId,
			String courseId, String semesterId, String questionId, @NotBlank String sourceItemId,
			String sourceRef, String analysis) {}
	public record MasteryRequest(@NotBlank String status, String note) {}
}
