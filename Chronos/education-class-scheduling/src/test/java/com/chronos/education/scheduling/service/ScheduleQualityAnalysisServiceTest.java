package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePolicy;

class ScheduleQualityAnalysisServiceTest {
	@Test
	void changedTeachingWeekPatternBlocksPublishingOldDraftUntilRescheduled() {
		var entries = mock(ScheduleEntryRepository.class);
		var offerings = mock(CourseOfferingRepository.class);
		var classrooms = mock(ClassroomRepository.class);
		var teachers = mock(TeacherAcademicProfileRepository.class);
		var policies = mock(SchedulePolicyService.class);
		var members = mock(TeachingClassMemberRepository.class);
		var terms = mock(AcademicTermRepository.class);
		var calendarDays = mock(AcademicCalendarDayRepository.class);
		var bellSchedules = mock(BellScheduleRepository.class);
		var bellPeriods = mock(BellPeriodRepository.class);
		var unavailable = mock(ClassroomUnavailableSlotRepository.class);
		var service = new ScheduleQualityAnalysisService(entries, offerings, classrooms,
				teachers, policies, members, terms, calendarDays, bellSchedules,
				bellPeriods, unavailable);

		var offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setStatus("ACTIVE");
		offering.setCourseName("专业基础");
		offering.setTeachingClassName("一班");
		offering.setTeacherId("teacher-1");
		offering.setTeacherName("张老师");
		offering.setWeeklyLessons(1);
		offering.setWeekPattern("ODD");
		var entry = new ScheduleEntry();
		entry.setOfferingId("offering-1");
		entry.setClassroomId("room-1");
		entry.setDayOfWeek(1);
		entry.setPeriodNo(1);
		entry.setWeekPattern("ALL");
		var policy = new SchedulePolicy();
		var term = new AcademicTerm();
		term.setId("term-1");
		when(policies.resolve("term-code")).thenReturn(policy);
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("term-code"))
				.thenReturn(List.of(entry));
		when(offerings.findBySemesterCodeOrderByOfferingCode("term-code"))
				.thenReturn(List.of(offering));
		when(classrooms.findAll()).thenReturn(List.of());
		when(teachers.findAll()).thenReturn(List.of());
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(),
				org.mockito.ArgumentMatchers.eq("ENROLLED"))).thenReturn(List.of());
		when(terms.findByTermCode("term-code")).thenReturn(Optional.of(term));
		when(calendarDays.findByAcademicTermIdOrderByCalendarDate("term-1"))
				.thenReturn(List.of());
		when(unavailable.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc("term-code"))
				.thenReturn(List.of());

		assertThat(service.publishBlockers("term-code"))
				.containsExactly("专业基础 / 一班 的课表授课周模式与教学任务不一致，请重新排课");
		entry.setWeekPattern("ODD");
		assertThat(service.publishBlockers("term-code")).isEmpty();
	}
}
