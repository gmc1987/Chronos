package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulingAiLockedEntry;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiSoftPriority;
import com.chronos.education.scheduling.model.SchedulingAiSlotRule;
import com.chronos.education.scheduling.model.SchedulingAiWeekRule;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchedulingAgentPlanValidatorTest {
	@Test
	void revalidatesRuleEvidenceAndTeacherAgainstCurrentScope() throws Exception {
		var scopes = mock(EducationDataScopeService.class);
		var timetable = mock(SchedulingAgentTimetableService.class);
		var offerings = mock(CourseOfferingRepository.class);
		var teachers = mock(TeacherAcademicProfileRepository.class);
		var scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		var offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setTeacherId("teacher-1");
		offering.setCourseName("生物实验");
		offering.setStatus("ACTIVE");
		var teacher = new TeacherAcademicProfile();
		teacher.setId("teacher-1");
		teacher.setTeacherName("张老师");
		teacher.setEnabled(true);
		when(scopes.resolve("admin")).thenReturn(scope);
		when(timetable.dimensions("2026-2027-1", "GLOBAL", Set.of()))
				.thenReturn(new SchedulingAgentTimetableService.Dimensions(5, 8, 18));
		when(timetable.targetOfferingIds("2026-2027-1", "GLOBAL", Set.of()))
				.thenReturn(Set.of("offering-1"));
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering));
		when(scopes.visibleOfferings(scope, List.of(offering))).thenReturn(List.of(offering));
		when(teachers.findById("teacher-1")).thenReturn(Optional.of(teacher));
		var validator = new SchedulingAgentPlanValidator(scopes,
				new ObjectMapper().findAndRegisterModules(), timetable, offerings,
				mock(ScheduleEntryRepository.class), teachers);
		var rule = new SchedulingAiSlotRule("TEACHER", "teacher-1",
				"张老师", 3, 1, "张老师周三第1节不能上课");
		var plan = new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1", "GLOBAL", Set.of(), 1, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rule));

		assertThat(validator.parameters(plan, "admin").constraints().slotExclusions())
				.containsExactly(new com.chronos.education.scheduling.model.ScheduleRunConstraints.SlotExclusion(
						"TEACHER", "teacher-1", 3, 1));
		var stale = new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1", "GLOBAL", Set.of(), 1, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(new SchedulingAiSlotRule("TEACHER", "teacher-1", "张老师", 4, 1,
						"张老师周三第1节不能上课")));
		assertThatThrownBy(() -> validator.parameters(stale, "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("用户原文");
		String multiple = "张老师周一、周三第1节和第2节不能上课";
		var incomplete = new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1", "GLOBAL", Set.of(), 1, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(new SchedulingAiSlotRule("TEACHER", "teacher-1", "张老师", 1, 1, multiple),
						new SchedulingAiSlotRule("TEACHER", "teacher-1", "张老师", 1, 2, multiple),
						new SchedulingAiSlotRule("TEACHER", "teacher-1", "张老师", 3, 1, multiple)));
		assertThatThrownBy(() -> validator.parameters(incomplete, "admin"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("完整覆盖");
		teacher.setEnabled(false);
		assertThatThrownBy(() -> validator.parameters(plan, "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("教师");
	}

	@Test
	void confirmedRunRebuildsRulesOnlyWhenTheirSourceRecordsStillMatch() throws Exception {
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		SchedulingAgentTimetableService timetable = mock(SchedulingAgentTimetableService.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		ScheduleEntryRepository entries = mock(ScheduleEntryRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setSemesterCode("2026-2027-1");
		offering.setOfferingCode("BIO-T1");
		offering.setCourseCode("BIO-101");
		offering.setCourseName("生物实验");
		offering.setTeachingClassName("生物实验一班");
		offering.setTeacherId("teacher-1");
		offering.setStatus("ACTIVE");
		ScheduleEntry entry = new ScheduleEntry();
		entry.setId("entry-17");
		entry.setSemesterCode("2026-2027-1");
		entry.setOfferingId("offering-1");
		entry.setStatus("SCHEDULED");
		TeacherAcademicProfile teacher = new TeacherAcademicProfile();
		teacher.setId("teacher-1");
		teacher.setTeacherName("张老师");
		teacher.setTeacherNo("T001");
		teacher.setEnabled(true);
		when(scopes.resolve("admin")).thenReturn(scope);
		when(timetable.dimensions("2026-2027-1", "GLOBAL", Set.of()))
				.thenReturn(new SchedulingAgentTimetableService.Dimensions(5, 8, 18));
		when(timetable.targetOfferingIds("2026-2027-1", "GLOBAL", Set.of()))
				.thenReturn(Set.of("offering-1"));
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering));
		when(scopes.visibleOfferings(scope, List.of(offering))).thenReturn(List.of(offering));
		when(entries.findById("entry-17")).thenReturn(Optional.of(entry));
		when(teachers.findById("teacher-1")).thenReturn(Optional.of(teacher));
		SchedulingAgentPlanValidator validator = new SchedulingAgentPlanValidator(scopes,
				new ObjectMapper().findAndRegisterModules(), timetable, offerings, entries, teachers);
		SchedulingAiPlan plan = new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1", "GLOBAL", Set.of(), 1, List.of(), List.of(), List.of(),
				List.of(), List.of(),
				List.of(new SchedulingAiWeekRule("offering-1", "ODD", 1, 17,
						"BIO-T1仅单周")),
				List.of(new SchedulingAiLockedEntry("entry-17", "offering-1", 3, 4,
						"保留现有课表条目 entry-17")),
				List.of(new SchedulingAiSoftPriority("TEACHER_GAP", "teacher-1",
						"张老师", "张老师减少空档"),
						new SchedulingAiSoftPriority("SAME_DAY", "teacher-1",
								"张老师", "张老师同一天集中")));
		entry.setDayOfWeek(3);
		entry.setPeriodNo(4);
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(entry));
		AgentRun run = new AgentRun();
		run.setSemesterCode("2026-2027-1");
		run.setConfirmedPlanJson(new ObjectMapper().findAndRegisterModules().writeValueAsString(plan));

		var parameters = validator.fromConfirmedRun(run, "admin");

		assertThat(parameters.constraints().weekRules()).containsExactly(
				new com.chronos.education.scheduling.model.ScheduleRunConstraints.WeekRule(
						"offering-1", "ODD", 1, 17));
		assertThat(parameters.constraints().lockedEntries()).containsExactly(
				new com.chronos.education.scheduling.model.ScheduleRunConstraints.LockedEntry(
						"entry-17", "offering-1", 3, 4));
		assertThat(parameters.constraints().softPriorities()).containsExactly(
				new com.chronos.education.scheduling.model.ScheduleRunConstraints.SoftPriority(
						"TEACHER_GAP", "teacher-1"),
				new com.chronos.education.scheduling.model.ScheduleRunConstraints.SoftPriority(
						"SAME_DAY", "teacher-1"));

		entry.setPeriodNo(5);
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(entry));
		assertThatThrownBy(() -> validator.fromConfirmedRun(run, "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("已不存在或已变更");
		entry.setPeriodNo(4);
		entry.setOfferingId("offering-other");
		assertThatThrownBy(() -> validator.fromConfirmedRun(run, "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("已不存在或已变更");
	}

	@Test
	void confirmedWeekRangeCannotExceedCurrentTermDimensions() {
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		SchedulingAgentTimetableService timetable = mock(SchedulingAgentTimetableService.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setOfferingCode("BIO-T1");
		offering.setCourseCode("BIO-101");
		offering.setCourseName("生物实验");
		offering.setTeachingClassName("生物实验一班");
		offering.setStatus("ACTIVE");
		when(scopes.resolve("admin")).thenReturn(scope);
		when(timetable.dimensions(any(), any(), any()))
				.thenReturn(new SchedulingAgentTimetableService.Dimensions(5, 8, 18));
		when(timetable.targetOfferingIds(any(), any(), any())).thenReturn(Set.of("offering-1"));
		when(offerings.findBySemesterCodeOrderByOfferingCode(any())).thenReturn(List.of(offering));
		when(scopes.visibleOfferings(any(), any())).thenReturn(List.of(offering));
		var validator = new SchedulingAgentPlanValidator(scopes,
				new ObjectMapper().findAndRegisterModules(), timetable, offerings,
				mock(ScheduleEntryRepository.class), mock(TeacherAcademicProfileRepository.class));
		var plan = new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1", "GLOBAL", Set.of(), 1, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(new SchedulingAiWeekRule(
						"offering-1", "ODD", 1, 20, "BIO-T1仅单周")), List.of(), List.of());

		assertThatThrownBy(() -> validator.parameters(plan, "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("当前学期数据不一致");
		var noOddWeek = new SchedulingAiPlan(1, "SCHEDULE_REQUIREMENTS_V1",
				"2026-2027-1", "GLOBAL", Set.of(), 1, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(new SchedulingAiWeekRule(
						"offering-1", "ODD", 2, 2, "BIO-T1单周第2周至第2周")),
				List.of(), List.of());
		assertThatThrownBy(() -> validator.parameters(noOddWeek, "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("当前学期数据不一致");
	}
}
