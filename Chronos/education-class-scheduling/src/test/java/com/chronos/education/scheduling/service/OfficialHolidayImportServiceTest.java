package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.service.OfficialHolidayImportService.HolidayDate;
import com.fasterxml.jackson.databind.ObjectMapper;

class OfficialHolidayImportServiceTest {
	@Test
	void importsMountedYearWithoutDownloading(@TempDir Path directory) throws Exception {
		Files.writeString(directory.resolve("2026.json"), """
				{"year":2026,"papers":["https://www.gov.cn/notice"],"days":[
				{"date":"2026-10-01","name":"国庆节","isOffDay":true}]}
				""");
		var calendar = mock(AcademicCalendarService.class);
		var service = new OfficialHolidayImportService(
				new ObjectMapper(), mock(AcademicTermRepository.class), calendar, directory.toString());

		assertThat(service.importOfficial("term", 2026).year()).isEqualTo(2026);
		verify(calendar).importHolidays("term",
				List.of(new HolidayDate(LocalDate.of(2026, 10, 1), "国庆节", true)), "AUTO", "https://www.gov.cn/notice");
	}
}
