package com.chronos.education.parentmeeting.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.chronos.education.parentmeeting.dao.ParentMeetingScopeRepository;
import com.chronos.education.parentmeeting.model.ParentMeetingCommands;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentProfile;
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
	@Mock EducationDataScopeService dataScope;
	@Mock IAuditLogService audit;
	@Mock IAdminUserRepository accounts;

	@Test
	void cannotCreateForStudentOutsideDataScope() {
		ParentMeetingService service = new ParentMeetingService(
				meetings, scopes, students, guardians, bindings, dataScope, audit, accounts);
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
				meetings, scopes, students, guardians, bindings, dataScope, audit, accounts);
		when(dataScope.resolve("teacher-1")).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
		assertThatThrownBy(() -> service.create(new ParentMeetingCommands.Create(
				"家长会", null, "ALL", "all", "ONLINE",
				java.time.LocalDateTime.now().plusHours(1),
				java.time.LocalDateTime.now().plusHours(2), null, null, null, "https://example.test", null),
				"teacher-1")).isInstanceOf(IllegalArgumentException.class);
	}
}
