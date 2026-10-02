package com.chronos;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.service.OfficialHolidayImportService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 演示学校副本上验证公告导入及人工配置保护；事务回滚保留原始验收数据。 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CHRONOS_DEMO_SCHEDULE_ACCEPTANCE", matches = "true")
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_DB_URL",
		matches = "jdbc:postgresql:.*/chronos_schedule_verify_[^/?]+(\\?.*)?")
class SchedulingHolidayImportAcceptanceTests {
	@Autowired private AcademicTermRepository terms;
	@Autowired private AcademicCalendarDayRepository days;
	@Autowired private OfficialHolidayImportService importer;

	@Test
	void officialImportAddsHolidayAndPreservesSchoolSpecificWorkday() {
		String termId = terms.findByTermCode("2026-2027-1").orElseThrow().getId();
		var existing = days.findByAcademicTermIdAndCalendarDate(
				termId, LocalDate.of(2026, 10, 10)).orElseThrow();
		var result = importer.importOfficial(termId, 2026);
		assertThat(result.counts().get("inserted") + result.counts().get("updated"))
				.isPositive();
		assertThat(days.findByAcademicTermIdAndCalendarDate(
				termId, LocalDate.of(2026, 9, 25)).orElseThrow())
				.satisfies(day -> {
					assertThat(day.getDayType()).isEqualTo("HOLIDAY");
					assertThat(day.getTeachingDay()).isFalse();
					assertThat(day.getSourceType()).isEqualTo("AUTO");
				});
		assertThat(days.findByAcademicTermIdAndCalendarDate(
				termId, LocalDate.of(2026, 10, 10)).orElseThrow())
				.satisfies(day -> {
					assertThat(day.getId()).isEqualTo(existing.getId());
					assertThat(day.getTeachingDay()).isEqualTo(existing.getTeachingDay());
					assertThat(day.getDayName()).isEqualTo("调休教学日");
					assertThat(day.getSourceType()).isEqualTo("MANUAL");
				});
	}

	@Test
	void manualImportCanOverrideAutomaticRecordButNotExistingSchoolRecord() {
		String termId = terms.findByTermCode("2026-2027-1").orElseThrow().getId();
		importer.importOfficial(termId, 2026);
		String content = """
				{"year":2026,"days":[
				  {"date":"2026-09-25","name":"中秋校历确认","isOffDay":true},
				  {"date":"2026-10-10","name":"不应覆盖","isOffDay":true}
				]}
				""";
		var result = importer.importManual(termId, 2026, content);
		assertThat(result.counts().get("updated")).isEqualTo(1);
		assertThat(result.counts().get("skipped")).isEqualTo(1);
		assertThat(days.findByAcademicTermIdAndCalendarDate(
				termId, LocalDate.of(2026, 9, 25)).orElseThrow().getSourceType())
				.isEqualTo("MANUAL");
		assertThat(days.findByAcademicTermIdAndCalendarDate(
				termId, LocalDate.of(2026, 10, 10)).orElseThrow().getDayName())
				.isEqualTo("调休教学日");
	}
}
