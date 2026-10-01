package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.chronos.education.scheduling.model.EducationDataScope;

/** Supplies persisted scheduling conflict facts for a snapshot scope. */
public interface SchedulingConflictProvider {
	Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope);
}
