package com.chronos.education.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.model.AcademicCalendarDay;
import com.chronos.education.scheduling.model.AcademicTerm;

class AcademicCalendarImportTest {
	private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
	private final AcademicCalendarDayRepository days = mock(AcademicCalendarDayRepository.class);
	private final AcademicCalendarService service = new AcademicCalendarService(
			terms, days, mock(BellScheduleRepository.class), mock(BellPeriodRepository.class));

	@BeforeEach
	void setUp() {
		AcademicTerm term = new AcademicTerm();
		term.setStartDate(LocalDate.of(2026, 9, 1));
		term.setEndDate(LocalDate.of(2027, 1, 30));
		when(terms.findById("term")).thenReturn(Optional.of(term));
	}

	@Test
	void previewDoesNotWrite() {
		var result = service.importCalendarDays("term", List.of(day()), true);
		assertEquals(List.of(), result.errors());
		assertEquals(0, result.imported());
		verify(days, never()).save(any());
	}

	@Test
	void duplicateRejectsEntireBatch() {
		var result = service.importCalendarDays("term", List.of(day(), day()), false);
		assertEquals(List.of("第 2 条：文件中日期重复"), result.errors());
		verify(days, never()).save(any());
	}

	@Test
	void validBatchWrites() {
		var result = service.importCalendarDays("term", List.of(day()), false);
		assertEquals(1, result.imported());
		verify(days).save(any(AcademicCalendarDay.class));
	}

	private AcademicCalendarDay day() {
		AcademicCalendarDay day = new AcademicCalendarDay();
		day.setCalendarDate(LocalDate.of(2026, 10, 1));
		day.setDayName("国庆节");
		return day;
	}
}
