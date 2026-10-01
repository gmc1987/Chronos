package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.chronos.education.scheduling.model.EducationDataScope;

/** Supplies invigilation workload only when assignments can be scoped to the date and campus. */
public interface InvigilationWorkloadProvider {
	Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope);
}
