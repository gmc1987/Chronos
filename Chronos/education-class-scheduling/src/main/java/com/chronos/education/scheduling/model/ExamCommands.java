package com.chronos.education.scheduling.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ExamCommands {
	private ExamCommands() {
	}

	public record Plan(
			String semesterCode,
			String planName,
			String examType,
			LocalDate startDate,
			LocalDate endDate,
			Integer baseInvigilators,
			Integer extraStaffThreshold,
			Boolean allowOwnClassInvigilation,
			String ruleJson,
			Integer maxConsecutiveDuties,
			Integer campusTravelMinutes,
			Boolean requireSubjectQualification) {
	}

	public record Session(
			String subjectId,
			LocalDate examDate,
			LocalTime startTime,
			LocalTime endTime) {
	}

	public record Room(String classroomId, Integer requiredInvigilators) {
	}

	public record Candidates(List<String> studentIds) {
	}

	public record Assignment(String teacherId, String dutyRole) {
	}

	public record Change(String proposedTeacherId, String reason) {
	}

	public record Decision(boolean approve, String replacementTeacherId) {
	}

	public record PublishedChange(
			String changeType,
			String sessionId,
			String roomId,
			LocalDate newExamDate,
			LocalTime newStartTime,
			LocalTime newEndTime,
			String newClassroomId,
			String reason) {
	}
}
