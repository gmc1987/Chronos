package com.chronos.education.parentmeeting.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.meeting.service.MeetingCenterService;
import com.chronos.education.meeting.model.Meeting;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingView;
import com.chronos.education.parentmeeting.dao.ParentMeetingScopeRepository;
import com.chronos.education.parentmeeting.model.ParentMeetingCommands;
import com.chronos.education.parentmeeting.model.ParentMeetingScope;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.Idao.IAdminUserRepository;

@ExtendWith(MockitoExtension.class)
class ParentMeetingServiceTest {
	@Mock MeetingCenterService meetings;
	@Mock ParentMeetingScopeRepository scopes;
	@Mock StudentProfileRepository students;
	@Mock StudentGuardianRepository guardians;
	@Mock EducationUserBindingRepository bindings;
	@Mock ParentProfileRepository parents;
	@Mock EducationDataScopeService dataScope;
	@Mock IAuditLogService audit;
	@Mock IAdminUserRepository accounts;

	@Test
	void cannotCreateForStudentOutsideDataScope() {
		ParentMeetingService service = new ParentMeetingService(
				meetings, scopes, students, guardians, bindings, parents, dataScope, audit, accounts);
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setEnrollmentStatus("ACTIVE");
		EducationDataScope limited = new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(dataScope.resolve("teacher-1")).thenReturn(limited);
		org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
				.when(dataScope).assertStudentAccess(limited, "student-1");

		assertThatThrownBy(() -> service.create(new ParentMeetingCommands.Create(
				"家长会", null, "STUDENT", "student-1", "ONLINE",
				java.time.LocalDateTime.now().plusHours(1),
				java.time.LocalDateTime.now().plusHours(2), null, null, null, "https://example.test", null),
				"teacher-1")).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void rejectsScopeTypeOutsideHomeSchoolDesign() {
		ParentMeetingService service = new ParentMeetingService(
				meetings, scopes, students, guardians, bindings, parents, dataScope, audit, accounts);
		when(dataScope.resolve("teacher-1")).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
		assertThatThrownBy(() -> service.create(new ParentMeetingCommands.Create(
				"家长会", null, "ALL", "all", "ONLINE",
				java.time.LocalDateTime.now().plusHours(1),
				java.time.LocalDateTime.now().plusHours(2), null, null, null, "https://example.test", null),
				"teacher-1")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void revokedParentBindingCannotReadMeetingInvitations() {
		ParentMeetingService service = new ParentMeetingService(
				meetings, scopes, students, guardians, bindings, parents, dataScope, audit, accounts);
		when(bindings.findByUsernameAndProfileTypeAndStatus("parent-1", "PARENT", "ACTIVE"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.mine("parent-1"))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void removedGuardianshipHidesInvitationAndRejectsResponse() {
		ParentMeetingService service = new ParentMeetingService(
				meetings, scopes, students, guardians, bindings, parents, dataScope, audit, accounts);
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileId("parent-profile-1");
		ParentProfile parent = new ParentProfile();
		parent.setId("parent-profile-1");
		parent.setStatus("ACTIVE");
		ParentMeetingScope scope = new ParentMeetingScope();
		scope.setMeetingId("meeting-1");
		scope.setScopeType("CLASS");
		scope.setScopeId("class-1");
		Meeting meeting = new Meeting();
		meeting.setId("meeting-1");
		MeetingView view = new MeetingView(meeting, null, List.of(), null, "parent-1", List.of(), null, List.of());
		when(bindings.findByUsernameAndProfileTypeAndStatus("parent-1", "PARENT", "ACTIVE"))
				.thenReturn(Optional.of(binding));
		when(parents.findById("parent-profile-1")).thenReturn(Optional.of(parent));
		when(scopes.findAll()).thenReturn(List.of(scope));
		when(scopes.findByMeetingId("meeting-1")).thenReturn(List.of(scope));
		when(meetings.myMeetings("parent-1")).thenReturn(List.of(view));
		when(guardians.findByParentIdOrderByCreateTime("parent-profile-1")).thenReturn(List.of());

		assertThat(service.mine("parent-1")).isEmpty();
		assertThatThrownBy(() -> service.respond("meeting-1", new MeetingCommands.Response("ACCEPTED", ""), "parent-1"))
				.isInstanceOf(AccessDeniedException.class);
	}
}
