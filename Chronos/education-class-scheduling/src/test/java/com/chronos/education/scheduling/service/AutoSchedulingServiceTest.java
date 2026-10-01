package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleCandidatePlanRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidatePlan;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePolicy;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

class AutoSchedulingServiceTest {
	@Test
	void teacherDayConcentrationPriorityChangesOnlyAiCandidate() throws Exception {
		var candidates = mock(ScheduleCandidatePlanRepository.class);
		var entries = mock(ScheduleEntryRepository.class);
		var offerings = mock(CourseOfferingRepository.class);
		var classrooms = mock(ClassroomRepository.class);
		var members = mock(TeachingClassMemberRepository.class);
		var terms = mock(AcademicTermRepository.class);
		var calendar = mock(AcademicCalendarService.class);
		CourseOffering course = offerings(1).getFirst();
		course.setWeeklyLessons(2);
		List<ScheduleCandidatePlan> saved = new ArrayList<>();
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(course));
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(1));
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of());
		when(calendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(2)))
				.thenReturn(Set.of(1, 2));
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan plan = invocation.getArgument(0);
			plan.setId("candidate-" + saved.size());
			saved.add(plan);
			return plan;
		});
		var solver = new AutoSchedulingService(candidates, entries, offerings, classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				mock(TeacherTimeConstraintRepository.class), mock(TeacherAcademicProfileRepository.class),
				members, terms, calendar, policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class), mock(IAuditLogService.class),
				mock(EntityManager.class));
		var command = new AutoScheduleCommand("2026-2027-1", "集中偏好", "FULL",
				Set.of(), 1, 2, 2, 1, 20);
		var priority = new ScheduleRunConstraints(List.of(), List.of(), List.of(), List.of(),
				List.of(new ScheduleRunConstraints.SoftPriority("SAME_DAY", "teacher-0")));

		solver.generate(command, "admin", () -> false, progress -> { }, priority);
		solver.generate(command, "admin");

		assertThat(readEntries(saved.get(0).getSnapshotJson()))
				.extracting(ScheduleEntry::getDayOfWeek).containsExactly(1, 1);
		assertThat(readEntries(saved.get(1).getSnapshotJson()))
				.extracting(ScheduleEntry::getDayOfWeek).containsExactly(1, 2);
		assertThat(saved.get(0).getTotalScore()).isGreaterThan(saved.get(1).getTotalScore());
		assertThat(new ObjectMapper().readValue(saved.get(0).getMetricsJson(),
				com.chronos.education.scheduling.model.ScheduleCandidateMetrics.class)
				.teacherDayConcentrationHits()).isEqualTo(1);
	}

	@Test
	void oddAndEvenWeeksShareResourcesButAllWeeksConflict() throws Exception {
		var candidates = mock(ScheduleCandidatePlanRepository.class);
		var entries = mock(ScheduleEntryRepository.class);
		var offerings = mock(CourseOfferingRepository.class);
		var classrooms = mock(ClassroomRepository.class);
		var members = mock(TeachingClassMemberRepository.class);
		var terms = mock(AcademicTermRepository.class);
		var calendar = mock(AcademicCalendarService.class);
		var courses = offerings(3);
		for (var course : courses) course.setTeacherId("one-teacher");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(courses);
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(1));
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of(member("offering-0", "student-1"),
						member("offering-1", "student-1"), member("offering-2", "student-1")));
		when(calendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(1)))
				.thenReturn(Set.of(1));
		List<ScheduleCandidatePlan> saved = new ArrayList<>();
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan plan = invocation.getArgument(0);
			plan.setId("candidate-" + saved.size());
			saved.add(plan);
			return plan;
		});
		var solver = new AutoSchedulingService(candidates, entries, offerings, classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				mock(TeacherTimeConstraintRepository.class), mock(TeacherAcademicProfileRepository.class),
				members, terms, calendar, policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class), mock(IAuditLogService.class),
				mock(EntityManager.class));
		var command = new AutoScheduleCommand("2026-2027-1", "周次", "FULL",
				Set.of(), 1, 1, 1, 1, 20);
		var run = new ScheduleRunConstraints(List.of(), List.of(), List.of(
				new ScheduleRunConstraints.WeekRule("offering-0", "ODD", 1, 20),
				new ScheduleRunConstraints.WeekRule("offering-1", "EVEN", 1, 20)),
				List.of(), List.of());

		solver.generate(command, "admin", () -> false, progress -> { }, run);
		solver.generate(command, "admin");

		assertThat(readEntries(saved.get(0).getSnapshotJson()))
				.extracting(ScheduleEntry::getWeekPattern).containsExactly("ODD", "EVEN");
		assertThat(saved.get(0).getUnscheduledLessons()).isEqualTo(1);
		assertThat(saved.get(1).getUnscheduledLessons()).isEqualTo(2);
	}

	@Test
	void temporaryLockKeepsOriginalEntryWithoutWritingFormalLock() throws Exception {
		var candidates = mock(ScheduleCandidatePlanRepository.class);
		var entries = mock(ScheduleEntryRepository.class);
		var offerings = mock(CourseOfferingRepository.class);
		var classrooms = mock(ClassroomRepository.class);
		var members = mock(TeachingClassMemberRepository.class);
		var terms = mock(AcademicTermRepository.class);
		var calendar = mock(AcademicCalendarService.class);
		ScheduleEntry original = new ScheduleEntry();
		original.setId("entry-1");
		original.setSemesterCode("2026-2027-1");
		original.setOfferingId("offering-0");
		original.setClassroomId("room-0");
		original.setDayOfWeek(1);
		original.setPeriodNo(1);
		original.setWeekPattern("ALL");
		original.setStartWeek(1);
		original.setEndWeek(20);
		original.setStatus("SCHEDULED");
		original.setLocked(false);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(original));
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(offerings(1));
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(1));
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of());
		when(calendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(2)))
				.thenReturn(Set.of(1, 2));
		AtomicReference<ScheduleCandidatePlan> saved = new AtomicReference<>();
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan value = invocation.getArgument(0);
			value.setId("candidate-1");
			saved.set(value);
			return value;
		});
		var solver = new AutoSchedulingService(candidates, entries, offerings, classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				mock(TeacherTimeConstraintRepository.class), mock(TeacherAcademicProfileRepository.class),
				members, terms, calendar, policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class), mock(IAuditLogService.class),
				mock(EntityManager.class));
		var command = new AutoScheduleCommand("2026-2027-1", "临时锁课", "FULL",
				Set.of(), 1, 1, 2, 1, 20);
		var lock = new ScheduleRunConstraints(List.of(), List.of(), List.of(),
				List.of(new ScheduleRunConstraints.LockedEntry("entry-1", "offering-0", 1, 1)), List.of());

		solver.generate(command, "admin", () -> false, progress -> { }, lock);
		assertThat(readEntries(saved.get().getSnapshotJson()))
				.singleElement().satisfies(entry -> {
					assertThat(entry.getId()).isEqualTo("entry-1");
					assertThat(entry.getLocked()).isFalse();
				});
		assertThatThrownBy(() -> solver.generate(command, "admin", () -> false,
				progress -> { }, new ScheduleRunConstraints(List.of(), List.of(),
						List.of(new ScheduleRunConstraints.WeekRule("offering-0", "ODD", 1, 20)),
						lock.lockedEntries(), List.of())))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("授课周");
	}

	@Test
	void scopedTeacherRuleChangesOnlyAiCandidateNotOrdinaryScheduling() throws Exception {
		ScheduleCandidatePlanRepository candidates = mock(ScheduleCandidatePlanRepository.class);
		ScheduleEntryRepository entries = mock(ScheduleEntryRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		ClassroomRepository classrooms = mock(ClassroomRepository.class);
		TeacherTimeConstraintRepository constraints = mock(TeacherTimeConstraintRepository.class);
		TeachingClassMemberRepository members = mock(TeachingClassMemberRepository.class);
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		AcademicCalendarService calendar = mock(AcademicCalendarService.class);
		List<ScheduleCandidatePlan> saved = new ArrayList<>();
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(offerings(1));
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(1));
		when(constraints.findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of());
		when(calendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(2)))
				.thenReturn(Set.of(1, 2));
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan plan = invocation.getArgument(0);
			plan.setId("candidate-" + saved.size());
			saved.add(plan);
			return plan;
		});
		AutoSchedulingService service = new AutoSchedulingService(
				candidates, entries, offerings, classrooms,
				mock(ClassroomUnavailableSlotRepository.class), constraints,
				mock(TeacherAcademicProfileRepository.class), members, terms, calendar,
				policyService(), mock(com.chronos.Idao.IAdminUserRepository.class),
				mock(IAuditLogService.class), mock(EntityManager.class));
		AutoScheduleCommand command = new AutoScheduleCommand(
				"2026-2027-1", "隔离验证", "FULL", Set.of(), 1, 1, 2, 1, 20);

		service.generate(command, "admin", () -> false, progress -> { },
				new ScheduleRunConstraints(List.of(
						new ScheduleRunConstraints.TeacherSlot("teacher-0", 1, 1, "FORBIDDEN"))));
		service.generate(command, "admin");

		assertThat(readEntries(saved.get(0).getSnapshotJson()).getFirst().getPeriodNo()).isEqualTo(2);
		assertThat(readEntries(saved.get(1).getSnapshotJson()).getFirst().getPeriodNo()).isEqualTo(1);
		var slotRule = new ScheduleRunConstraints(List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(new ScheduleRunConstraints.SlotExclusion(
						"TEACHER", "teacher-0", 1, 1)));
		service.generate(command, "admin", () -> false, progress -> { }, slotRule);
		assertThat(readEntries(saved.get(2).getSnapshotJson()).getFirst().getPeriodNo()).isEqualTo(2);
		assertThat(new ObjectMapper().readValue(saved.get(2).getMetricsJson(),
				com.chronos.education.scheduling.model.ScheduleCandidateMetrics.class)
				.slotRuleChecks()).containsExactly(
						new com.chronos.education.scheduling.model.ScheduleCandidateMetrics.SlotRuleCheck(
								"TEACHER", "teacher-0", 1, 1, 0));
		var existing = new ScheduleEntry();
		existing.setId("existing-1");
		existing.setSemesterCode("2026-2027-1");
		existing.setOfferingId("offering-0");
		existing.setClassroomId("room-0");
		existing.setDayOfWeek(1);
		existing.setPeriodNo(1);
		existing.setDurationPeriods(2);
		existing.setWeekPattern("ALL");
		existing.setStartWeek(1);
		existing.setEndWeek(20);
		existing.setStatus("SCHEDULED");
		existing.setLocked(true);
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(existing));
		var overlappingBlock = new ScheduleRunConstraints(List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(new ScheduleRunConstraints.SlotExclusion(
						"TEACHER", "teacher-0", 1, 2)));
		assertThatThrownBy(() -> service.generate(command, "admin", () -> false,
				progress -> { }, overlappingBlock))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("禁排规则冲突");
		assertThat(saved).hasSize(3);
		verify(constraints, org.mockito.Mockito.never()).save(any());
	}

	@Test
	void scopedBlockPreferenceKeepsOrdinarySchedulingAndFallsBackToSinglePeriods() throws Exception {
		ScheduleCandidatePlanRepository candidates = mock(ScheduleCandidatePlanRepository.class);
		ScheduleEntryRepository entries = mock(ScheduleEntryRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		ClassroomRepository classrooms = mock(ClassroomRepository.class);
		AcademicCalendarService calendar = mock(AcademicCalendarService.class);
		TeachingClassMemberRepository members = mock(TeachingClassMemberRepository.class);
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		List<ScheduleCandidatePlan> saved = new ArrayList<>();
		CourseOffering course = offerings(1).getFirst();
		course.setWeeklyLessons(2);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(course));
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(1));
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of());
		when(calendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), any(Integer.class)))
				.thenReturn(Set.of(1, 2));
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan plan = invocation.getArgument(0);
			plan.setId("candidate-" + saved.size());
			saved.add(plan);
			return plan;
		});
		var service = new AutoSchedulingService(candidates, entries, offerings, classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				mock(TeacherTimeConstraintRepository.class), mock(TeacherAcademicProfileRepository.class),
				members, terms, calendar, policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class), mock(IAuditLogService.class),
				mock(EntityManager.class));
		var command = new AutoScheduleCommand("2026-2027-1", "连堂", "FULL",
				Set.of(), 1, 1, 2, 1, 20);
		var blockRule = new ScheduleRunConstraints(List.of(),
				List.of(new ScheduleRunConstraints.OfferingDuration("offering-0", 2)));
		service.generate(command, "admin", () -> false, progress -> { }, blockRule);
		service.generate(command, "admin");
		assertThat(readEntries(saved.get(0).getSnapshotJson()))
				.extracting(ScheduleEntry::getDurationPeriods).containsExactly(2);
		var savedMetrics = new ObjectMapper().readValue(saved.get(0).getMetricsJson(),
				com.chronos.education.scheduling.model.ScheduleCandidateMetrics.class);
		assertThat(savedMetrics.consecutiveBlockHits()).isEqualTo(1);
		assertThat(readEntries(saved.get(1).getSnapshotJson()))
				.extracting(ScheduleEntry::getDurationPeriods).containsExactly(1, 1);
		// A single schedulable period cannot fit a block, but must still place one lesson.
		when(calendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), any(Integer.class)))
				.thenReturn(Set.of(1));
		service.generate(command, "admin", () -> false, progress -> { }, blockRule);
		assertThat(readEntries(saved.get(2).getSnapshotJson()))
				.extracting(ScheduleEntry::getDurationPeriods).containsExactly(1);
		assertThat(saved.get(2).getUnscheduledLessons()).isEqualTo(1);
		assertThat(new ObjectMapper().readValue(saved.get(2).getMetricsJson(),
				com.chronos.education.scheduling.model.ScheduleCandidateMetrics.class)
				.consecutiveBlockHits()).isZero();
	}

	@Test
	void schedulesTwoThousandOfferingsWithinCapacityUsingScopedMemberQuery() throws Exception {
		ScheduleCandidatePlanRepository candidates = mock(ScheduleCandidatePlanRepository.class);
		ScheduleEntryRepository entries = mock(ScheduleEntryRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		ClassroomRepository classrooms = mock(ClassroomRepository.class);
		TeacherTimeConstraintRepository constraints = mock(TeacherTimeConstraintRepository.class);
		TeachingClassMemberRepository members = mock(TeachingClassMemberRepository.class);
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		AcademicCalendarService academicCalendar = mock(AcademicCalendarService.class);
		AtomicReference<ScheduleCandidatePlan> savedCandidate = new AtomicReference<>();
		List<CourseOffering> offeringRows = offerings(2_000);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(academicCalendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(8)))
				.thenReturn(Set.of(1, 2, 3, 4, 5, 6, 7, 8));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(offeringRows);
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(50));
		when(constraints.findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of());
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan value = invocation.getArgument(0);
			value.setId("candidate-1");
			savedCandidate.set(value);
			return value;
		});
		AutoSchedulingService service = new AutoSchedulingService(
				candidates,
				entries,
				offerings,
				classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				constraints,
				mock(TeacherAcademicProfileRepository.class),
				members,
				terms,
				academicCalendar,
				policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class),
				mock(IAuditLogService.class),
				mock(EntityManager.class));
		AutoScheduleCommand command = new AutoScheduleCommand(
				"2026-2027-1",
				"容量回归",
				"FULL",
				Set.of(),
				1,
				5,
				8,
				1,
				20);

		var result = assertTimeout(
				Duration.ofSeconds(10),
				() -> service.generate(command, "tester"));

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().entryCount()).isEqualTo(2_000);
		assertThat(result.getFirst().unscheduledLessons()).isZero();
		List<ScheduleEntry> generated = readEntries(savedCandidate.get().getSnapshotJson());
		assertThat(generated).hasSize(2_000);
		Set<String> occupiedRooms = new HashSet<>();
		for (ScheduleEntry entry : generated) {
			assertThat(occupiedRooms.add(
					entry.getDayOfWeek() + "|"
							+ entry.getPeriodNo() + "|"
							+ entry.getClassroomId()))
						.isTrue();
		}
		verify(members).findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED"));
	}

	@Test
	void preservesTeacherAndStudentHardConflictsWhenUsingSchedulingIndex() throws Exception {
		ScheduleCandidatePlanRepository candidates = mock(ScheduleCandidatePlanRepository.class);
		ScheduleEntryRepository entries = mock(ScheduleEntryRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		ClassroomRepository classrooms = mock(ClassroomRepository.class);
		TeacherTimeConstraintRepository constraints = mock(TeacherTimeConstraintRepository.class);
		TeachingClassMemberRepository members = mock(TeachingClassMemberRepository.class);
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		AcademicCalendarService academicCalendar = mock(AcademicCalendarService.class);
		AtomicReference<ScheduleCandidatePlan> savedCandidate = new AtomicReference<>();
		List<CourseOffering> offeringRows = offerings(3);
		// 第三个任务与第一个任务教师相同；第二个任务与第一个任务学生相同。
		offeringRows.get(2).setTeacherId(offeringRows.getFirst().getTeacherId());
		TeachingClassMember firstMember = member("offering-0", "student-1");
		TeachingClassMember secondMember = member("offering-1", "student-1");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(academicCalendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(1)))
				.thenReturn(Set.of(1, 2, 3, 4, 5, 6, 7, 8));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(offeringRows);
		when(classrooms.findByEnabledTrueOrderByRoomCode()).thenReturn(classrooms(3));
		when(constraints.findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of());
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), eq("ENROLLED")))
				.thenReturn(List.of(firstMember, secondMember));
		when(candidates.save(any())).thenAnswer(invocation -> {
			ScheduleCandidatePlan value = invocation.getArgument(0);
			value.setId("candidate-conflict");
			savedCandidate.set(value);
			return value;
		});
		AutoSchedulingService service = new AutoSchedulingService(
				candidates,
				entries,
				offerings,
				classrooms,
				mock(ClassroomUnavailableSlotRepository.class),
				constraints,
				mock(TeacherAcademicProfileRepository.class),
				members,
				terms,
				academicCalendar,
				policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class),
				mock(IAuditLogService.class),
				mock(EntityManager.class));
		AutoScheduleCommand command = new AutoScheduleCommand(
				"2026-2027-1",
				"硬冲突回归",
				"FULL",
				Set.of(),
				1,
				1,
				1,
				1,
				20);

		var result = service.generate(command, "tester");

		assertThat(result.getFirst().entryCount()).isEqualTo(1);
		assertThat(result.getFirst().unscheduledLessons()).isEqualTo(2);
		assertThat(readEntries(savedCandidate.get().getSnapshotJson()))
				.extracting(ScheduleEntry::getOfferingId)
				.containsExactly("offering-0");
	}

	@Test
	void readsLegacyCandidateMetricsWhenListingExistingPlans() {
		ScheduleCandidatePlanRepository candidates = mock(ScheduleCandidatePlanRepository.class);
		ScheduleCandidatePlan legacy = new ScheduleCandidatePlan();
		legacy.setId("legacy-candidate");
		legacy.setSemesterCode("2026-2027-1");
		legacy.setPlanName("历史候选方案");
		legacy.setGenerationMode("FULL");
		legacy.setMetricsJson(
				"{\"hardConflicts\":0,\"softConflicts\":3,\"roomUtilization\":0.78,\"teacherBalance\":0.86}");
		when(candidates.findBySemesterCodeOrderByGeneratedAtDesc("2026-2027-1"))
				.thenReturn(List.of(legacy));

		AutoSchedulingService service = new AutoSchedulingService(
				candidates,
				mock(ScheduleEntryRepository.class),
				mock(CourseOfferingRepository.class),
				mock(ClassroomRepository.class),
				mock(ClassroomUnavailableSlotRepository.class),
				mock(TeacherTimeConstraintRepository.class),
				mock(TeacherAcademicProfileRepository.class),
				mock(TeachingClassMemberRepository.class),
				mock(AcademicTermRepository.class),
				mock(AcademicCalendarService.class),
				policyService(),
				mock(com.chronos.Idao.IAdminUserRepository.class),
				mock(IAuditLogService.class),
				mock(EntityManager.class));

		var result = service.list("2026-2027-1");

		assertThat(result).singleElement().satisfies(candidate -> {
			assertThat(candidate.id()).isEqualTo("legacy-candidate");
			assertThat(candidate.metrics().scheduledLessons()).isZero();
			assertThat(candidate.metrics().totalScore()).isZero();
		});
	}

	private static SchedulePolicyService policyService() {
		SchedulePolicyService service = mock(SchedulePolicyService.class);
		SchedulePolicy policy = new SchedulePolicy();
		policy.setSemesterCode("2026-2027-1");
		when(service.resolve(any())).thenReturn(policy);
		return service;
	}

	private List<CourseOffering> offerings(int count) {
		List<CourseOffering> values = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			CourseOffering offering = new CourseOffering();
			offering.setId("offering-" + index);
			offering.setSemesterCode("2026-2027-1");
			offering.setOfferingCode("O%04d".formatted(index));
			offering.setCourseCode("C%04d".formatted(index));
			offering.setCourseName("课程" + index);
			offering.setTeachingClassName("教学班" + index);
			offering.setTeacherId("teacher-" + index);
			offering.setTeacherName("教师" + index);
			offering.setStudentCount(30);
			offering.setWeeklyLessons(1);
			offering.setCampusId("campus-1");
			offering.setStatus("ACTIVE");
			values.add(offering);
		}
		return values;
	}

	private List<Classroom> classrooms(int count) {
		List<Classroom> values = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			Classroom classroom = new Classroom();
			classroom.setId("room-" + index);
			classroom.setRoomCode("R%02d".formatted(index));
			classroom.setRoomName("教室" + index);
			classroom.setCampusId("campus-1");
			classroom.setCapacity(50);
			classroom.setEnabled(true);
			values.add(classroom);
		}
		return values;
	}

	private TeachingClassMember member(String offeringId, String studentId) {
		TeachingClassMember value = new TeachingClassMember();
		value.setOfferingId(offeringId);
		value.setStudentId(studentId);
		value.setEnrollmentStatus("ENROLLED");
		return value;
	}

	private List<ScheduleEntry> readEntries(String snapshot) throws Exception {
		return new ObjectMapper()
				.findAndRegisterModules()
				.readValue(snapshot, new TypeReference<>() { });
	}
}
