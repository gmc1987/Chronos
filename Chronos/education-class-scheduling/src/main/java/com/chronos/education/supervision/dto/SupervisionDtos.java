package com.chronos.education.supervision.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class SupervisionDtos {
	private SupervisionDtos() {}

	public record PlanCommand(
			@NotBlank @Size(max = 200) String name,
			@NotNull LocalDate startDate,
			@NotNull LocalDate endDate,
			@Size(max = 64) String campusId) {}

	public record AssignmentCommand(
			@NotBlank @Size(max = 64) String planId,
			@NotBlank @Size(max = 64) String supervisorId,
			@NotBlank @Size(max = 64) String teacherId,
			@NotBlank @Size(max = 64) String scheduleEntryId,
			@Size(max = 64) String campusId) {}

	public record EvaluationCommand(
			@NotBlank String formTemplateId) {}

	public record CheckInCommand(@NotBlank String proof) {}

	public record IssueCommand(
			@NotBlank @Size(max = 16) String severity,
			@NotBlank @Size(max = 200) String title,
			@Size(max = 10000) String description,
			@Size(max = 64) String ownerId,
			LocalDateTime dueAt) {}

	public record RectificationCommand(@NotBlank @Size(max = 10000) String content) {}

	public record ReviewCommand(@NotNull Boolean approved, @Size(max = 2000) String comment) {}

	public record PlanResponse(String id, String name, String status, LocalDate startDate, LocalDate endDate) {}
	public record AssignmentResponse(String id, String planId, String teacherId, String scheduleEntryId, String status,
			LocalDateTime checkedInAt, LocalDateTime submittedAt) {}
	public record RecordResponse(String id, String assignmentId, String supervisorId, String teacherId,
			String formTemplateId, String formSnapshotJson, String scheduleContextSnapshotJson, LocalDateTime submittedAt) {}
	public record IssueResponse(String id, String recordId, String severity, String title, String status,
			String ownerId, LocalDateTime dueAt) {}
}
