package com.chronos.education.scheduling.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record ExamStudentView(
		String candidateId,
		String planName,
		String subjectId,
		String subjectName,
		LocalDate examDate,
		LocalTime startTime,
		LocalTime endTime,
		String classroomName,
		Integer seatNo) {
}
