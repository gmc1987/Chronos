package com.chronos.education.scheduling.model;

/** Run-local soft preference resolved to one authorized teacher. */
public record SchedulingAiSoftPriority(
		String kind, String teacherId, String teacherName, String sourceText) {
}
