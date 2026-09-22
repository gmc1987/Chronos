package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import com.chronos.education.scheduling.model.EducationDataScope;
import org.springframework.stereotype.Component;

/** Invigilation assignments do not persist their exam session/date or campus. */
@Component
public class UnavailableInvigilationWorkloadProvider implements InvigilationWorkloadProvider {
	public Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope) {
		return Optional.empty();
	}
}
