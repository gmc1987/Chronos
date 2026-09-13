package com.chronos.education.scheduling.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Strongly typed first-slice commands. Status, owner and school are never client supplied. */
public final class TeachingPlanDtos {
	private TeachingPlanDtos() {}

	public enum PlanType { SEMESTER, UNIT, PRACTICAL }
	public enum LessonType { REGULAR, PRACTICAL, REVIEW }

	public record PlanItemRequest(
			@NotNull @Positive Integer chapterNo,
			@NotBlank @Size(max = 200) String chapterName,
			@NotNull @Min(1) @Max(60) Integer weekStart,
			@NotNull @Min(1) @Max(60) Integer weekEnd,
			@NotNull @Positive Integer lessonHours,
			@Min(0) Integer trainingHours,
			@NotBlank String objectives,
			@NotBlank String keyPoints,
			@NotBlank String difficultPoints,
			String assessmentMethod,
			@Size(max = 64) String linkedKnowledgePointId,
			@NotNull @Min(0) Integer sortOrder) {}

	public record PlanCreateRequest(
			@NotBlank @Size(max = 64) String offeringId,
			@NotBlank @Size(max = 64) String academicTermId,
			@NotBlank @Size(max = 200) String name,
			@NotNull PlanType planType,
			@NotNull @Positive Integer totalHours,
			@NotBlank String objective,
			String assessmentMethod,
			String remarks,
			@Valid List<PlanItemRequest> items) {}

	public record PlanUpdateRequest(
			@NotNull Long rowVersion,
			@NotBlank @Size(max = 200) String name,
			@NotNull PlanType planType,
			@NotNull @Positive Integer totalHours,
			@NotBlank String objective,
			String assessmentMethod,
			String remarks,
			@Valid List<PlanItemRequest> items) {}

	public record LessonCreateRequest(
			@NotBlank @Size(max = 64) String offeringId,
			@Size(max = 64) String scheduleEntryId,
			@Size(max = 64) String teachingPlanId,
			@Size(max = 64) String planItemId,
			@NotBlank @Size(max = 200) String title,
			@NotNull @Positive Integer lessonNo,
			@NotNull @Min(1) @Max(60) Integer teachingWeek,
			@NotNull @Positive Integer lessonHours,
			@NotNull LessonType lessonType,
			@NotBlank String objectives,
			@NotBlank String keyPoints,
			@NotBlank String difficultPoints,
			String teachingMethod, String classroomActivity,
			String assessmentDesign, String afterClassReflection,
			String safetyNotes, String equipmentRequirements,
			@Size(max = 64) String fileId) {}

	public record LessonUpdateRequest(
			@NotNull Long rowVersion,
			@NotBlank @Size(max = 200) String title,
			@NotNull @Positive Integer lessonNo,
			@NotNull @Min(1) @Max(60) Integer teachingWeek,
			@NotNull @Positive Integer lessonHours,
			@NotNull LessonType lessonType,
			@NotBlank String objectives, @NotBlank String keyPoints,
			@NotBlank String difficultPoints, String teachingMethod,
			String classroomActivity, String assessmentDesign,
			String afterClassReflection, String safetyNotes,
			String equipmentRequirements, @Size(max = 64) String fileId) {}
}
