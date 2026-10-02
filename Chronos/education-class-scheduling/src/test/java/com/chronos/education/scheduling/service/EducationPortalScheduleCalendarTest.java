package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EducationPortalScheduleCalendarTest {
	private AcademicTermRepository terms;
	private CourseOfferingRepository offerings;
	private ClassroomRepository classrooms;
	private TeacherAcademicProfileRepository teachers;
	private EducationUserBindingRepository bindings;
	private SchedulePlanVersionService planVersions;
	private ScheduleOccurrenceService occurrences;
	private BellScheduleRepository bellSchedules;
	private BellPeriodRepository bellPeriods;
	private EducationPortalContributionProvider service;

	@BeforeEach
	void setUp() {
		terms = mock(AcademicTermRepository.class);
		offerings = mock(CourseOfferingRepository.class);
		classrooms = mock(ClassroomRepository.class);
		teachers = mock(TeacherAcademicProfileRepository.class);
		bindings = mock(EducationUserBindingRepository.class);
		planVersions = mock(SchedulePlanVersionService.class);
		occurrences = mock(ScheduleOccurrenceService.class);
		bellSchedules = mock(BellScheduleRepository.class);
		bellPeriods = mock(BellPeriodRepository.class);
		service = new EducationPortalContributionProvider(
				mock(IAdminUserRepository.class),
				teachers,
				mock(StudentProfileRepository.class),
				mock(AdministrativeClassRepository.class),
				offerings,
				classrooms,
				terms,
				bellSchedules,
				bellPeriods,
				planVersions,
				bindings,
				mock(TeachingClassMemberRepository.class),
				mock(StudentGuardianRepository.class),
				occurrences);
	}

	@Test
	void calendarIncludesBellTimesForPreparingLesson() {
		LocalDate date = LocalDate.of(2026, 9, 21);
		AcademicTerm term = new AcademicTerm();
		ReflectionTestUtils.setField(term, "id", "term-1");
		term.setTermCode("2026-FALL");
		term.setTermName("2026年秋季学期");
		when(terms.findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc("ACTIVE"))
				.thenReturn(Optional.of(term));

		CourseOffering offering = new CourseOffering();
		ReflectionTestUtils.setField(offering, "id", "offering-1");
		offering.setTeacherId("teacher-1");
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-FALL"))
				.thenReturn(List.of(offering));
		Classroom classroom = new Classroom();
		ReflectionTestUtils.setField(classroom, "id", "room-1");
		classroom.setCampusId("campus-1");
		when(classrooms.findAll()).thenReturn(List.of(classroom));

		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileType("TEACHER");
		binding.setProfileId("teacher-1");
		when(bindings.findByUsernameAndStatusOrderByProfileType("teacher", "ACTIVE"))
				.thenReturn(List.of(binding));
		when(teachers.findAll()).thenReturn(List.of());

		BellSchedule bellSchedule = new BellSchedule();
		ReflectionTestUtils.setField(bellSchedule, "id", "bell-1");
		bellSchedule.setCampusId("campus-1");
		bellSchedule.setDefaultSchedule(true);
		bellSchedule.setStatus("ACTIVE");
		when(bellSchedules.findByAcademicTermIdOrderByScheduleName("term-1"))
				.thenReturn(List.of(bellSchedule));
		BellPeriod period = new BellPeriod();
		period.setPeriodNo(2);
		period.setStartTime(java.time.LocalTime.of(9, 0));
		period.setEndTime(java.time.LocalTime.of(9, 45));
		when(bellPeriods.findByBellScheduleIdOrderByPeriodNo("bell-1"))
				.thenReturn(List.of(period));

		ScheduleEntry entry = new ScheduleEntry();
		when(planVersions.latestPublishedEntries("2026-FALL")).thenReturn(List.of(entry));
		ScheduleEntryView entryView = new ScheduleEntryView(
				"entry-1", "2026-FALL", "offering-1", "O-1", "数学",
				"一班", "任课教师", "room-1", "101", 1, 2, 1,
				"ALL", 1, 20, "PUBLISHED", false, 0L);
		ScheduleOccurrenceView occurrence = new ScheduleOccurrenceView(
				"entry-1@2026-09-21", date, entryView, "SCHEDULED", null,
				null, null, 2, "room-1", null);
		when(occurrences.publishedOccurrences(eq("2026-FALL"), eq(date), any()))
				.thenReturn(List.of(occurrence));

		Map<String, Object> result = service.personalScheduleCalendar(
				"teacher", null, date, date);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> values = (List<Map<String, Object>>) result.get("occurrences");
		assertThat(values.getFirst())
				.containsEntry("startTime", "09:00")
				.containsEntry("endTime", "09:45");
	}

	@Test
	void substituteTeacherCanSeeAssignedOccurrence() {
		LocalDate date = LocalDate.of(2026, 9, 21);
		AcademicTerm term = new AcademicTerm();
		term.setTermCode("2026-FALL");
		term.setTermName("2026年秋季学期");
		when(terms.findFirstByCurrentTermTrueAndStatusOrderByStartDateDesc("ACTIVE"))
				.thenReturn(Optional.of(term));

		CourseOffering offering = new CourseOffering();
		ReflectionTestUtils.setField(offering, "id", "offering-1");
		offering.setTeacherId("teacher-original");
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-FALL"))
				.thenReturn(List.of(offering));

		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileType("TEACHER");
		binding.setProfileId("teacher-substitute");
		when(bindings.findByUsernameAndStatusOrderByProfileType("substitute", "ACTIVE"))
				.thenReturn(List.of(binding));

		TeacherAcademicProfile substitute = new TeacherAcademicProfile();
		ReflectionTestUtils.setField(substitute, "id", "teacher-substitute");
		substitute.setTeacherName("代课教师");
		when(teachers.findAll()).thenReturn(List.of(substitute));

		ScheduleEntry entry = new ScheduleEntry();
		when(planVersions.latestPublishedEntries("2026-FALL")).thenReturn(List.of(entry));
		ScheduleEntryView entryView = new ScheduleEntryView(
				"entry-1", "2026-FALL", "offering-1", "O-1", "语文",
				"一班", "原教师", "room-1", "101", 1, 1, 1,
				"ALL", 1, 20, "PUBLISHED", false, 0L);
		ScheduleOccurrenceView occurrence = new ScheduleOccurrenceView(
				"entry-1@2026-09-21", date, entryView, "SUBSTITUTED", "SUBSTITUTE",
				"exception-1", "临时代课", 1, "room-1", "teacher-substitute");
		when(occurrences.publishedOccurrences(eq("2026-FALL"), eq(date), any()))
				.thenReturn(List.of(occurrence));

		Map<String, Object> result = service.personalScheduleCalendar(
				"substitute", null, date, date);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> values = (List<Map<String, Object>>) result.get("occurrences");
		assertThat(values).hasSize(1);
		assertThat(values.getFirst().get("effectiveTeacherName")).isEqualTo("代课教师");
		assertThat(values.getFirst().get("occurrenceStatus")).isEqualTo("SUBSTITUTED");
	}

	@Test
	void calendarRejectsRangesLongerThanThirtyOneDays() {
		LocalDate start = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> service.personalScheduleCalendar(
				"teacher", null, start, start.plusDays(31)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("单次最多查询31天课表");
	}
}
