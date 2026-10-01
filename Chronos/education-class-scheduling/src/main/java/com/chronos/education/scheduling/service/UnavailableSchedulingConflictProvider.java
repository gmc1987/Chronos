package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import com.chronos.education.scheduling.model.EducationDataScope;
import org.springframework.stereotype.Component;

/** Schedule entries are semester/week based and cannot be safely mapped to a daily snapshot. */
@Component
public class UnavailableSchedulingConflictProvider implements SchedulingConflictProvider {
	public Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope) {
		return Optional.empty();
	}
}
