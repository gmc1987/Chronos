package com.chronos.education.scheduling.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public record ExamDutyView(
		String assignmentId,
		String planName,
		String subjectId,
		String subjectName,
		LocalDate examDate,
		LocalTime startTime,
		LocalTime endTime,
		String classroomName,
		String dutyRole,
		String status,
		LocalDateTime acknowledgedAt,
		String assignmentStatus,
		LocalDateTime checkedInAt) {
}
