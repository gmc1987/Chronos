package com.chronos.education.scheduling.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePlanVersion;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.workflow.WorkflowNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulePublicationNotificationServiceTest {
	@Test
	void notifiesBoundTeachersAndStudentsOncePerUsername() {
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeachingClassMemberRepository members = mock(TeachingClassMemberRepository.class);
		EducationUserBindingRepository bindings = mock(EducationUserBindingRepository.class);
		WorkflowNotificationService notifications = mock(WorkflowNotificationService.class);
		SchedulePublicationNotificationService service = new SchedulePublicationNotificationService(
				offerings,
				members,
				bindings,
				notifications);

		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setTeacherId("teacher-1");
		when(offerings.findAllById(anyList())).thenReturn(List.of(offering));
		TeachingClassMember member = new TeachingClassMember();
		member.setStudentId("student-1");
		when(members.findByOfferingIdInAndEnrollmentStatus(anyList(), org.mockito.ArgumentMatchers.eq("ENROLLED")))
				.thenReturn(List.of(member));
		when(bindings.findByProfileTypeAndProfileIdInAndStatus(
				org.mockito.ArgumentMatchers.eq("TEACHER"),
				anyList(),
				org.mockito.ArgumentMatchers.eq("ACTIVE")))
				.thenReturn(List.of(binding("shared.user", "TEACHER", "teacher-1")));
		when(bindings.findByProfileTypeAndProfileIdInAndStatus(
				org.mockito.ArgumentMatchers.eq("STUDENT"),
				anyList(),
				org.mockito.ArgumentMatchers.eq("ACTIVE")))
				.thenReturn(List.of(
						binding("student.user", "STUDENT", "student-1"),
						binding("shared.user", "STUDENT", "student-1")));

		ScheduleEntry entry = new ScheduleEntry();
		entry.setOfferingId("offering-1");
		SchedulePlanVersion version = new SchedulePlanVersion();
		version.setId("version-3");
		version.setSemesterCode("2026-2027-1");
		version.setVersionNo(3);

		service.enqueue(version, List.of(entry, entry), false);

		verify(notifications, times(1)).enqueueUserEvent(
				"EDUCATION_SCHEDULE_PUBLISHED",
				"version-3",
				"shared.user",
				"课表已发布",
				"2026-2027-1 学期课表已发布，当前版本 V3，请及时查看个人课表。",
				"V3");
		verify(notifications, times(1)).enqueueUserEvent(
				"EDUCATION_SCHEDULE_PUBLISHED",
				"version-3",
				"student.user",
				"课表已发布",
				"2026-2027-1 学期课表已发布，当前版本 V3，请及时查看个人课表。",
				"V3");
	}

	private EducationUserBinding binding(
			String username,
			String profileType,
			String profileId) {
		EducationUserBinding value = new EducationUserBinding();
		value.setUsername(username);
		value.setProfileType(profileType);
		value.setProfileId(profileId);
		return value;
	}
}
