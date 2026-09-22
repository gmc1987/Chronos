package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.chronos.education.scheduling.model.EducationDataScope;

/** Supplies a utilization value only when its persisted source has complete dimensions. */
public interface SchedulingUtilizationProvider {
	Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope);
}
