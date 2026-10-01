package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import com.chronos.education.scheduling.model.EducationDataScope;
import org.springframework.stereotype.Component;

/** No persisted source currently contains the denominator required for utilization. */
@Component
public class UnavailableSchedulingUtilizationProvider implements SchedulingUtilizationProvider {
	public Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope) {
		return Optional.empty();
	}
}
