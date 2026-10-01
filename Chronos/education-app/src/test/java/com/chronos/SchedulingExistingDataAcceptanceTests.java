package com.chronos;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleCandidatePlanRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.service.AutoSchedulingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs only against an explicitly selected, isolated copy of the existing education database.
 * All candidates and audit records are rolled back after each test.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CHRONOS_ACCEPTANCE_CLONE", matches = "true")
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_DB_URL",
		matches = "jdbc:postgresql:.*/[^/?]*(test|verify)[^/?]*(\\?.*)?")
class SchedulingExistingDataAcceptanceTests {
	private static final String TERM = "2026-2027-1";
	private final ObjectMapper json = new ObjectMapper();

	@Autowired
	private CourseOfferingRepository offerings;
	@Autowired
	private AcademicTermRepository terms;
	@Autowired
	private ClassroomRepository classrooms;
	@Autowired
	private BellScheduleRepository schedules;
	@Autowired
	private BellPeriodRepository periods;
	@Autowired
	private ScheduleEntryRepository entries;
	@Autowired
	private ScheduleCandidatePlanRepository candidates;
	@Autowired
	private AutoSchedulingService scheduling;

	@Test
	@Transactional
	void existingTimetableSupportsTemporaryLockWithoutChangingOrdinaryScheduling() throws Exception {
		ScheduleEntry original = sampleEntry();
		AutoScheduleCommand command = command(original);
		var lock = new ScheduleRunConstraints(List.of(), List.of(), List.of(),
				List.of(new ScheduleRunConstraints.LockedEntry(original.getId(),
						original.getOfferingId(), original.getDayOfWeek(), original.getPeriodNo())),
				List.of());

		var ai = scheduling.generate(command, "acceptance-clone", () -> false,
				ignored -> { }, lock).getFirst();
		var regular = scheduling.generate(command, "acceptance-clone").getFirst();

		var snapshot = json.readTree(candidates.findById(ai.id()).orElseThrow().getSnapshotJson());
		assertThat(snapshot).anySatisfy(entry -> assertThat(entry.path("id").asText())
				.isEqualTo(original.getId()));
		assertThat(candidates.findById(regular.id()).orElseThrow().getStatus())
				.isEqualTo("CANDIDATE");
		assertThat(entries.findById(original.getId()).orElseThrow().getLocked()).isFalse();
		assertThat(entries.findById(original.getId()).orElseThrow().getDayOfWeek())
				.isEqualTo(original.getDayOfWeek());
	}

	@Test
	@Transactional
	void existingOfferingAcceptsRunLocalOddWeekRule() throws Exception {
		ScheduleEntry original = sampleEntry();
		var rule = new ScheduleRunConstraints(List.of(), List.of(),
				List.of(new ScheduleRunConstraints.WeekRule(original.getOfferingId(), "ODD", 1, 20)),
				List.of(), List.of());

		var result = scheduling.generate(command(original), "acceptance-clone",
				() -> false, ignored -> { }, rule).getFirst();
		var snapshot = json.readTree(candidates.findById(result.id()).orElseThrow().getSnapshotJson());

		assertThat(snapshot).anySatisfy(entry -> {
			assertThat(entry.path("offeringId").asText()).isEqualTo(original.getOfferingId());
			assertThat(entry.path("weekPattern").asText()).isEqualTo("ODD");
		});
		assertThat(entries.findById(original.getId()).orElseThrow().getWeekPattern()).isEqualTo("ALL");
	}

	@Test
	@Transactional
	void missingAlternatingWeekDataCanBeSeededOnlyInsideTheAcceptanceTransaction() throws Exception {
		CourseOffering source = offerings.findBySemesterCodeOrderByOfferingCode(TERM).stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.findFirst().orElseThrow();
		var room = classrooms.findByEnabledTrueOrderByRoomCode().stream()
				.filter(classroom -> classroom.getCapacity() > 0 && classroom.getCampusId() != null)
				.findFirst().orElseThrow();
		String code = "AI-VERIFY-" + UUID.randomUUID().toString().substring(0, 8);
		AcademicTerm term = new AcademicTerm();
		term.setTermCode(code);
		term.setTermName(code);
		term.setAcademicYear("2026-2027");
		term.setTermNo(1);
		term.setStartDate(LocalDate.of(2026, 9, 1));
		term.setEndDate(LocalDate.of(2027, 1, 15));
		term.setWeekCount(20);
		term = terms.saveAndFlush(term);

		BellSchedule schedule = new BellSchedule();
		schedule.setAcademicTermId(term.getId());
		schedule.setCampusId(room.getCampusId());
		schedule.setScheduleCode(code);
		schedule.setScheduleName(code);
		schedule.setDefaultSchedule(true);
		schedule = schedules.saveAndFlush(schedule);
		BellPeriod period = new BellPeriod();
		period.setBellScheduleId(schedule.getId());
		period.setPeriodNo(1);
		period.setPeriodName("Period 1");
		period.setDaySegment("MORNING");
		period.setStartTime(LocalTime.of(8, 0));
		period.setEndTime(LocalTime.of(8, 45));
		period.setSchedulable(true);
		periods.saveAndFlush(period);

		CourseOffering odd = sampleOffering(code, "ODD", source, room.getCampusId());
		CourseOffering even = sampleOffering(code, "EVEN", source, room.getCampusId());
		var command = new AutoScheduleCommand(code, "Alternating weeks", "FULL",
				Set.of(), 1, 1, 1, 1, 20);
		var rules = new ScheduleRunConstraints(List.of(), List.of(),
				List.of(new ScheduleRunConstraints.WeekRule(odd.getId(), "ODD", 1, 20),
						new ScheduleRunConstraints.WeekRule(even.getId(), "EVEN", 1, 20)),
				List.of(), List.of());

		var ai = scheduling.generate(command, "acceptance-clone", () -> false,
				ignored -> { }, rules).getFirst();
		var regular = scheduling.generate(command, "acceptance-clone").getFirst();

		assertThat(ai.unscheduledLessons()).isZero();
		assertThat(regular.unscheduledLessons()).isEqualTo(1);
		var snapshot = json.readTree(candidates.findById(ai.id()).orElseThrow().getSnapshotJson());
		assertThat(snapshot).hasSize(2).extracting(entry -> entry.path("weekPattern").asText())
				.containsExactlyInAnyOrder("ODD", "EVEN");
		assertThat(offerings.findById(odd.getId()).orElseThrow().getWeekPattern()).isEqualTo("ALL");
		assertThat(offerings.findById(even.getId()).orElseThrow().getWeekPattern()).isEqualTo("ALL");
	}

	private CourseOffering sampleOffering(String termCode, String suffix,
			CourseOffering source, String campusId) {
		CourseOffering offering = new CourseOffering();
		offering.setSemesterCode(termCode);
		offering.setOfferingCode(termCode + "-" + suffix);
		offering.setCourseCode(source.getCourseCode());
		offering.setCourseName(source.getCourseName());
		offering.setTeachingClassName(termCode + "-" + suffix);
		offering.setTeacherId(source.getTeacherId());
		offering.setTeacherName(source.getTeacherName());
		offering.setCampusId(campusId);
		offering.setStudentCount(1);
		offering.setWeeklyLessons(1);
		return offerings.saveAndFlush(offering);
	}

	private ScheduleEntry sampleEntry() {
		Set<String> activeOfferings = offerings.findBySemesterCodeOrderByOfferingCode(TERM).stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.map(CourseOffering::getId)
				.collect(java.util.stream.Collectors.toSet());
		assertThat(activeOfferings).as("the copied term must contain active offerings").isNotEmpty();
		return entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(TERM).stream()
				.filter(entry -> activeOfferings.contains(entry.getOfferingId())
						&& "SCHEDULED".equals(entry.getStatus())
						&& !Boolean.TRUE.equals(entry.getLocked())
						&& entry.getDayOfWeek() <= 5 && entry.getPeriodNo() <= 8)
				.findFirst()
				.orElseThrow(() -> new AssertionError("the copied term needs an unlocked timetable entry"));
	}

	private AutoScheduleCommand command(ScheduleEntry entry) {
		return new AutoScheduleCommand(TERM, "Acceptance clone", "LOCAL",
				Set.of(entry.getOfferingId()), 1, 5, 8, 1, 20);
	}
}
