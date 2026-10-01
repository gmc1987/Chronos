package com.chronos.education.scheduling.model.dto;

import java.time.LocalDate;

public record StudentStatusChangeCommand(
		String changeType,
		String targetClassId,
		LocalDate effectiveDate,
		String reason) {
}
