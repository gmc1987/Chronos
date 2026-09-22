package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.chronos.education.scheduling.dao.CourseAdjustmentRecordRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import org.springframework.stereotype.Component;

/** Adapter over the adjustment records; campus-scoped counts are unavailable because records have no campus. */
@Component
public class PersistedCourseAdjustmentCountProvider implements CourseAdjustmentCountProvider {
	private final CourseAdjustmentRecordRepository records;

	public PersistedCourseAdjustmentCountProvider(CourseAdjustmentRecordRepository records) {
		this.records = records;
	}

	@Override
	public Optional<BigDecimal> measure(LocalDate date, String campusId, EducationDataScope scope) {
		if (date == null || campusId == null || !campusId.isBlank() || !scope.fullAccess()) {
			return Optional.empty();
		}
		LocalDateTime start = date.atStartOfDay();
		long count = records.countByCreateTimeBetween(start, date.plusDays(1).atStartOfDay());
		return Optional.of(BigDecimal.valueOf(count));
	}
}
