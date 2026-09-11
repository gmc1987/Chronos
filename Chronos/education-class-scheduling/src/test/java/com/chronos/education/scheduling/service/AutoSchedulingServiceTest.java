package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidatePlan;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

class AutoSchedulingServiceTest {
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
				members,
				terms,
				academicCalendar,
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
		when(academicCalendar.schedulablePeriodNumbers(eq("2026-2027-1"), any(), eq(8)))
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
				members,
				terms,
				academicCalendar,
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
