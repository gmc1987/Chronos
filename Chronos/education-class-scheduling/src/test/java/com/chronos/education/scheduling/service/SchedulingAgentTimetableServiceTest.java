package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.CourseOffering;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulingAgentTimetableServiceTest {
	private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
	private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
	private final BellScheduleRepository schedules = mock(BellScheduleRepository.class);
	private final BellPeriodRepository periods = mock(BellPeriodRepository.class);
	private final SchedulingAgentTimetableService service =
			new SchedulingAgentTimetableService(terms, offerings, schedules, periods);

	@BeforeEach
	void setUp() {
		AcademicTerm term = new AcademicTerm();
		term.setId("term-1");
		term.setWeekCount(16);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering("offering-1", "campus-1", "teacher-1")));
		BellSchedule schedule = new BellSchedule();
		schedule.setId("bell-1");
		when(schedules.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
				"term-1", "campus-1", "ACTIVE")).thenReturn(Optional.of(schedule));
		when(schedules.existsByAcademicTermIdAndStatus("term-1", "ACTIVE")).thenReturn(true);
		when(periods.findByBellScheduleIdOrderByPeriodNo("bell-1"))
				.thenReturn(List.of(period(1, "MORNING"), period(3, "AFTERNOON"),
						period(4, "AFTERNOON")));
	}

	@Test
	void mapsAfternoonFromDefaultCampusBellScheduleAndTermWeeks() {
		var resolved = service.segment("2026-2027-1", "teacher-1",
				"GLOBAL", Set.of(), "AFTERNOON");
		assertThat(resolved.periodNumbers()).containsExactly(3, 4);
		assertThat(resolved.clarification()).isNull();
		assertThat(service.dimensions("2026-2027-1", "GLOBAL", Set.of()))
				.isEqualTo(new SchedulingAgentTimetableService.Dimensions(5, 4, 16));
	}

	@Test
	void refusesAmbiguousCampusesAndMissingLocalTargets() {
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering("offering-1", "campus-1", "teacher-1"),
						offering("offering-2", "campus-2", "teacher-1")));
		assertThat(service.segment("2026-2027-1", "teacher-1",
				"GLOBAL", Set.of(), "AFTERNOON").clarification()).contains("唯一的校区");
		assertThatThrownBy(() -> service.dimensions("2026-2027-1",
				"LOCAL", Set.of("not-an-offering"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void requiresDefaultScheduleAndMatchingSchedulableSegment() {
		when(schedules.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
				"term-1", "campus-1", "ACTIVE")).thenReturn(Optional.empty());
		assertThat(service.segment("2026-2027-1", "teacher-1", "GLOBAL",
				Set.of(), "AFTERNOON").clarification()).contains("默认作息");
		assertThatThrownBy(() -> service.dimensions("2026-2027-1",
				"GLOBAL", Set.of())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("默认作息");
	}

	@Test
	void rejectsUnsupportedPeriodAndMissingTeacherCampus() {
		when(periods.findByBellScheduleIdOrderByPeriodNo("bell-1"))
				.thenReturn(List.of(period(21, "AFTERNOON"), period(4, "MORNING")));
		assertThat(service.segment("2026-2027-1", "teacher-1",
				"GLOBAL", Set.of(), "AFTERNOON").clarification()).contains("不受排课求解器");
		assertThatThrownBy(() -> service.dimensions("2026-2027-1",
				"GLOBAL", Set.of())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("支持范围");
		assertThat(service.segment("2026-2027-1", "teacher-unknown",
				"GLOBAL", Set.of(), "AFTERNOON").clarification()).contains("唯一的校区");
	}

	@Test
	void preservesLegacyEightPeriodFallbackOnlyWithoutActiveBellSchedules() {
		when(schedules.existsByAcademicTermIdAndStatus("term-1", "ACTIVE")).thenReturn(false);
		assertThat(service.dimensions("2026-2027-1", "GLOBAL", Set.of()))
				.isEqualTo(new SchedulingAgentTimetableService.Dimensions(5, 8, 16));
		when(schedules.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
				"term-1", "campus-1", "ACTIVE")).thenReturn(Optional.empty());
		assertThat(service.segment("2026-2027-1", "teacher-1",
				"GLOBAL", Set.of(), "AFTERNOON").clarification()).contains("默认作息");
	}

	@Test
	void allowsCampusNeutralOfferingsAlongsideConfiguredCampuses() {
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering("public-course", null, "teacher-2"),
						offering("specialized-course", "campus-1", "teacher-1")));
		assertThat(service.dimensions("2026-2027-1", "GLOBAL", Set.of()))
				.isEqualTo(new SchedulingAgentTimetableService.Dimensions(5, 4, 16));
	}

	private CourseOffering offering(String id, String campus, String teacher) {
		CourseOffering value = new CourseOffering();
		value.setId(id);
		value.setCampusId(campus);
		value.setTeacherId(teacher);
		value.setStatus("ACTIVE");
		return value;
	}

	private BellPeriod period(int number, String segment) {
		BellPeriod value = new BellPeriod();
		value.setPeriodNo(number);
		value.setDaySegment(segment);
		value.setSchedulable(true);
		return value;
	}
}
