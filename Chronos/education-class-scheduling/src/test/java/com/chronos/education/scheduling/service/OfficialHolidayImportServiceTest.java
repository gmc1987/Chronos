package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.service.OfficialHolidayImportService.HolidayDate;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OfficialHolidayImportServiceTest {
	@Captor
	private ArgumentCaptor<List<HolidayDate>> days;

	@Test
	void importsMountedYearWithoutDownloading(@TempDir Path directory) throws Exception {
		Files.writeString(directory.resolve("2026.json"), """
				{"year":2026,"papers":["https://www.gov.cn/notice"],"days":[
				{"date":"2026-10-01","name":"国庆节","isOffDay":true}]}
				""");
		var calendar = mock(AcademicCalendarService.class);
		var service = new OfficialHolidayImportService(
				new ObjectMapper(), mock(AcademicTermRepository.class), calendar, directory.toString(), "offline");

		assertThat(service.importOfficial("term", 2026).year()).isEqualTo(2026);
		verify(calendar).importHolidays("term",
				List.of(new HolidayDate(LocalDate.of(2026, 10, 1), "国庆节", true)), "AUTO", "https://www.gov.cn/notice");
	}

	@ParameterizedTest
	@CsvSource({
		"2026,33,6,2026-10-10,https://www.gov.cn/zhengce/content/202511/content_7047090.htm",
		"2027,29,5,2027-10-09,https://www.gov.cn/zhengce/content/202611/content_7085101.htm"
	})
	void importsBundledHolidaysAndMakeupDaysWithoutNetwork(
			int year, int offDays, int workDays, LocalDate makeupDay, String sourceUrl) {
		var calendar = mock(AcademicCalendarService.class);
		var service = new OfficialHolidayImportService(
				new ObjectMapper(), mock(AcademicTermRepository.class), calendar, "", "offline");

		assertThat(service.importOfficial("term", year).sourceUrl()).isEqualTo(sourceUrl);
		verify(calendar).importHolidays(eq("term"), days.capture(), eq("AUTO"), eq(sourceUrl));
		assertThat(days.getValue()).hasSize(offDays + workDays);
		assertThat(days.getValue().stream().filter(HolidayDate::offDay)).hasSize(offDays);
		assertThat(days.getValue()).contains(new HolidayDate(makeupDay, "国庆节", false));
	}
}
