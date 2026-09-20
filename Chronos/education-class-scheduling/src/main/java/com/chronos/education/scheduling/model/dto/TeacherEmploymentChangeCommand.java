package com.chronos.education.scheduling.model.dto;

import java.time.LocalDate;

public record TeacherEmploymentChangeCommand(
		String changeType,
		String targetDepartmentId,
		LocalDate effectiveDate,
		String reason) {
}
