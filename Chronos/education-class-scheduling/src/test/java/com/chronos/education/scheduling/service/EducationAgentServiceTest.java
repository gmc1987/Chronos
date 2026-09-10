package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.SchedulingAgentProposalRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulingAgentProposal;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class EducationAgentServiceTest {
	private TeacherAcademicProfileRepository teachers;
	private SchedulingAgentProposalRepository proposals;
	private CourseOfferingRepository offerings;
	private ScheduleEntryRepository entries;
	private TeacherTimeConstraintRepository constraints;
	private EducationDataScopeService dataScopes;
	private EducationAgentService service;
	private Authentication authentication;
	private EducationDataScope scope;

	@BeforeEach
	void setUp() {
		teachers = mock(TeacherAcademicProfileRepository.class);
		proposals = mock(SchedulingAgentProposalRepository.class);
		offerings = mock(CourseOfferingRepository.class);
		entries = mock(ScheduleEntryRepository.class);
		constraints = mock(TeacherTimeConstraintRepository.class);
		dataScopes = mock(EducationDataScopeService.class);
		service = new EducationAgentService(
				proposals,
				teachers,
				constraints,
				offerings,
				entries,
				dataScopes,
				mock(IAuditLogService.class));
		authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("campus.manager");
		scope = new EducationDataScope(
				false,
				Set.of("campus-1"),
				Set.of(),
				Set.of(),
				Set.of());
		when(dataScopes.resolve("campus.manager")).thenReturn(scope);
	}

	@Test
	void academicAnalysisOnlyCountsVisibleOfferingsAndEntries() {
		CourseOffering own = offering("offering-1", "teacher-1", "张老师");
		CourseOffering foreign = offering("offering-2", "teacher-2", "李老师");
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(own, foreign));
		when(dataScopes.visibleOfferings(scope, List.of(own, foreign)))
				.thenReturn(List.of(own));
		ScheduleEntry ownEntry = entry("entry-1", "offering-1");
		ScheduleEntry foreignEntry = entry("entry-2", "offering-2");
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(ownEntry, foreignEntry));
		TeacherAcademicProfile ownTeacher = teacher("teacher-1", "张老师");
		TeacherAcademicProfile foreignTeacher = teacher("teacher-2", "李老师");
		when(teachers.findAllByOrderByTeacherNo())
				.thenReturn(List.of(ownTeacher, foreignTeacher));
		when(dataScopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		when(dataScopes.canAccessTeacher(scope, "teacher-2")).thenReturn(false);
		when(constraints.findBySemesterCodeAndTeacherId("2026-2027-1", "teacher-1"))
				.thenReturn(List.of());

		EducationAgentService.AcademicAnalysis result = service.analyze(
				"2026-2027-1",
				authentication);

		assertThat(result.offeringCount()).isEqualTo(1);
		assertThat(result.scheduleEntryCount()).isEqualTo(1);
		assertThat(result.teacherLoads())
				.extracting(EducationAgentService.TeacherLoad::teacherId)
				.containsExactly("teacher-1");
	}

	@Test
	void proposalCannotResolveTeacherOutsideDataScope() {
		TeacherAcademicProfile foreignTeacher = teacher("teacher-2", "李老师");
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(foreignTeacher));
		when(dataScopes.canAccessTeacher(scope, "teacher-2")).thenReturn(false);

		assertThatThrownBy(() -> service.propose(
				new EducationAgentService.ProposalCommand(
						"2026-2027-1",
						"李老师星期一第1节不要排课"),
				authentication))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("未识别到教师姓名或工号");
	}

	@Test
	void confirmUsesLockedProposalAndCreatesSingleConstraint() {
		SchedulingAgentProposal proposal = new SchedulingAgentProposal();
		proposal.setId("proposal-1");
		proposal.setSemesterCode("2026-2027-1");
		proposal.setTeacherId("teacher-1");
		proposal.setDayOfWeek(1);
		proposal.setPeriodNo(2);
		proposal.setConstraintType("FORBIDDEN");
		proposal.setReason("星期一第2节禁排");
		proposal.setStatus("DRAFT");
		when(proposals.findLockedById("proposal-1")).thenReturn(Optional.of(proposal));
		when(proposals.save(proposal)).thenReturn(proposal);
		when(dataScopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		when(constraints.save(org.mockito.ArgumentMatchers.any()))
				.thenAnswer(invocation -> {
					var value = invocation.getArgument(0, com.chronos.education.scheduling.model.TeacherTimeConstraint.class);
					value.setId("constraint-1");
					return value;
				});

		SchedulingAgentProposal result = service.confirm("proposal-1", authentication);

		assertThat(result.getStatus()).isEqualTo("CONFIRMED");
		assertThat(result.getAppliedConstraintId()).isEqualTo("constraint-1");
		org.mockito.Mockito.verify(proposals).findLockedById("proposal-1");
		org.mockito.Mockito.verify(constraints, org.mockito.Mockito.times(1))
				.save(org.mockito.ArgumentMatchers.any());
	}

	private CourseOffering offering(
			String id,
			String teacherId,
			String teacherName) {
		CourseOffering value = new CourseOffering();
		value.setId(id);
		value.setTeacherId(teacherId);
		value.setTeacherName(teacherName);
		value.setWeeklyLessons(2);
		return value;
	}

	private ScheduleEntry entry(String id, String offeringId) {
		ScheduleEntry value = new ScheduleEntry();
		value.setId(id);
		value.setOfferingId(offeringId);
		value.setDayOfWeek(1);
		value.setPeriodNo(1);
		value.setDurationPeriods(1);
		return value;
	}

	private TeacherAcademicProfile teacher(String id, String name) {
		TeacherAcademicProfile value = new TeacherAcademicProfile();
		value.setId(id);
		value.setTeacherName(name);
		value.setMaxWeeklyLessons(20);
		return value;
	}
}
