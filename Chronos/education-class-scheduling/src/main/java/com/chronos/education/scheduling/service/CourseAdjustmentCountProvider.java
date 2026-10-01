package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.chronos.education.scheduling.model.EducationDataScope;

/** Supplies the count of persisted course-adjustment records for a snapshot day. */
public interface CourseAdjustmentCountProvider {
	Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope);
}
