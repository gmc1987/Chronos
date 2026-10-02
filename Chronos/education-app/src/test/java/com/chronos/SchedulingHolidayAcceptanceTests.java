package com.chronos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.model.AcademicCalendarDay;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.education.scheduling.service.AcademicCalendarService;
import com.chronos.education.scheduling.service.ScheduleOccurrenceService;
import com.chronos.education.scheduling.service.OfficialHolidayImportService;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 使用持久化演示学校副本验证假期和补班日；测试事务结束后不改动验收数据。 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CHRONOS_DEMO_SCHEDULE_ACCEPTANCE", matches = "true")
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_DB_URL",
		matches = "jdbc:postgresql:.*/chronos_schedule_verify_[^/?]+(\\?.*)?")
class SchedulingHolidayAcceptanceTests {
	private static final String TERM = "2026-2027-1";
	private static final LocalDate HOLIDAY = LocalDate.of(2026, 9, 25);
	private static final LocalDate MAKEUP_WORKDAY = LocalDate.of(2026, 9, 20);

	@Autowired private AcademicTermRepository terms;
	@Autowired private AcademicCalendarDayRepository days;
	@Autowired private AcademicCalendarService calendar;
	@Autowired private ScheduleOccurrenceService occurrences;
	@Autowired private OfficialHolidayImportService importer;

	@Test
	void holidayLessonsBecomePendingAndCanBeBroughtForwardWithoutChangingWeeklySchedule() {
		String termId = terms.findByTermCode(TERM).orElseThrow().getId();
		importer.importOfficial(termId, 2026);
		Set<String> original = occurrences.pendingMakeups(TERM).stream()
				.filter(item -> HOLIDAY.equals(item.sourceDate()))
				.map(item -> item.entry().id())
				.collect(Collectors.toSet());
		assertThat(original).isNotEmpty();
		assertThat(occurrences.occurrences(TERM, HOLIDAY)).isEmpty();

		AcademicCalendarDay workday = days
				.findByAcademicTermIdAndCalendarDate(termId, MAKEUP_WORKDAY).orElseThrow();
		workday.setDayType("MAKEUP_WORKDAY");
		workday.setDayName("调休补课");
		workday.setTeachingDay(true);
		workday.setScheduleDate(HOLIDAY);
		calendar.saveCalendarDay(workday.getId(), workday);
		assertThat(entryIds(MAKEUP_WORKDAY)).isEqualTo(original);
		assertThat(occurrences.pendingMakeups(TERM))
				.noneMatch(item -> HOLIDAY.equals(item.sourceDate()));
		ScheduleDateException duplicate = new ScheduleDateException();
		duplicate.setSemesterCode(TERM);
		duplicate.setSourceEntryId(original.iterator().next());
		duplicate.setSourceDate(HOLIDAY);
		duplicate.setExceptionType("MAKEUP");
		duplicate.setTargetDate(MAKEUP_WORKDAY);
		duplicate.setTargetPeriodNo(1);
		duplicate.setReason("重复补课验收");
		assertThatThrownBy(() -> occurrences.saveException(null, duplicate))
				.hasMessageContaining("不能重复补课");
	}

	private Set<String> entryIds(LocalDate date) {
		return occurrences.occurrences(TERM, date).stream()
				.map(item -> item.entry().id())
				.collect(Collectors.toSet());
	}
}
