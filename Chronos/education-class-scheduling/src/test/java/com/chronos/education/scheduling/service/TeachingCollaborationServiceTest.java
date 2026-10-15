package com.chronos.education.scheduling.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.CollaborationDtos.ResourceCopyRequest;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.service.ManagedFileService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class TeachingCollaborationServiceTest {
	@Mock PreparationRepository preparations;
	@Mock PreparationMemberRepository members;
	@Mock PreparationMaterialRepository prepMaterials;
	@Mock PreparationCommentRepository comments;
	@Mock CoursewareRepository coursewares;
	@Mock CoursewareVersionRepository coursewareVersions;
	@Mock TeachingMaterialRepository materials;
	@Mock TeachingMaterialVersionRepository materialVersions;
	@Mock CourseOfferingRepository offerings;
	@Mock ScheduleEntryRepository schedules;
	@Mock EducationDataScopeService scopes;
	@Mock ManagedFileRepository files;
	@Mock ManagedFileService managedFiles;
	@Mock TeachingReviewService reviews;
	@Mock EducationIdentityService identities;
	@Mock TeachingCollaborationNotificationService notifications;
	@Mock EntityManager entityManager;
	@Mock Authentication authentication;

	@Test
	void reinvitingRevokedTeacherReusesTheUniqueMemberRow() {
		var service = service();
		var preparation = new Preparation();
		preparation.setId("prep-1");
		preparation.setCreateBy("owner");
		var teacher = new TeacherAcademicProfile();
		teacher.setId("teacher-1");
		var member = new PreparationMember();
		member.setId("member-1");
		member.setPreparationId("prep-1");
		member.setTeacherId("teacher-1");
		member.setInvitationStatus("REVOKED");
		var scope = mock(EducationDataScope.class);
		when(authentication.getName()).thenReturn("owner");
		when(preparations.findById("prep-1")).thenReturn(Optional.of(preparation));
		when(scopes.resolve("owner")).thenReturn(scope);
		when(scope.fullAccess()).thenReturn(true);
		when(entityManager.find(TeacherAcademicProfile.class, "teacher-1")).thenReturn(teacher);
		when(members.findByPreparationId("prep-1")).thenReturn(List.of(member));
		when(members.save(member)).thenReturn(member);

		var result = service.invite("prep-1", new com.chronos.education.scheduling.model.dto.CollaborationDtos.MemberInviteRequest("teacher-1", "MEMBER"), authentication);

		assertSame(member, result);
		assertEquals("PENDING", result.getInvitationStatus());
		verify(members).save(member);
	}

	@Test
	void revokedCrossSchoolInvitationImmediatelyRemovesPreparationAccess() {
		var service = service();
		var preparation = new Preparation();
		preparation.setId("prep-1");
		preparation.setOfferingId("another-school-offering");
		preparation.setCreateBy("owner");
		var member = new PreparationMember();
		member.setId("member-1");
		member.setPreparationId("prep-1");
		member.setTeacherId("guest-teacher");
		member.setInvitationStatus("ACCEPT");
		var guestScope = mock(EducationDataScope.class);
		var ownerScope = mock(EducationDataScope.class);
		var guest = mock(Authentication.class);
		var owner = mock(Authentication.class);
		when(preparations.findById("prep-1")).thenReturn(Optional.of(preparation));
		when(members.findByPreparationId("prep-1")).thenReturn(List.of(member));
		when(members.findById("member-1")).thenReturn(Optional.of(member));
		when(members.save(member)).thenReturn(member);
		when(scopes.resolve("guest")).thenReturn(guestScope);
		when(scopes.resolve("owner")).thenReturn(ownerScope);
		when(ownerScope.fullAccess()).thenReturn(true);
		when(identities.teacherIds("guest")).thenReturn(Set.of("guest-teacher"));
		when(guest.getName()).thenReturn("guest");
		when(owner.getName()).thenReturn("owner");
		doThrow(new AccessDeniedException("跨校教学班不可访问"))
				.when(scopes).assertOfferingAccess(guestScope, "another-school-offering");

		assertSame(preparation, service.getPreparation("prep-1", guest));
		service.revokeInvitation("prep-1", "member-1", owner);
		assertEquals("REVOKED", member.getInvitationStatus());
		assertThrows(AccessDeniedException.class,
				() -> service.getPreparation("prep-1", guest));
	}

	@Test
	void generatesDraftCoursewareOnlyWithinPreparationOffering() {
		var service = service();
		var preparation = new Preparation();
		preparation.setId("prep-1");
		preparation.setOfferingId("offering-1");
		preparation.setTitle("第一章备课");
		var offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setCampusId("campus-1");
		offering.setTeacherId("teacher-1");
		when(authentication.getName()).thenReturn("teacher-1");
		when(preparations.findById("prep-1")).thenReturn(Optional.of(preparation));
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(coursewares.save(any(Courseware.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.createCoursewareFromPreparation(
				"prep-1",
				new ResourceCopyRequest("offering-1", "第一章课件", "PRIVATE", null, null, null),
				authentication);

		assertEquals("offering-1", result.getOfferingId());
		assertEquals("prep-1", result.getPreparationId());
		assertEquals("GENERATED", result.getSourceType());
		assertEquals("DRAFT", result.getStatus());
		verify(scopes).assertOfferingAccess(any(), eq("offering-1"));
	}

	@Test
	void rejectsGeneratingCoursewareForAnotherOffering() {
		var service = service();
		var preparation = new Preparation();
		preparation.setId("prep-1");
		preparation.setOfferingId("offering-1");
		when(preparations.findById("prep-1")).thenReturn(Optional.of(preparation));
		when(authentication.getName()).thenReturn("teacher-1");

		assertThrows(IllegalArgumentException.class, () -> service.createCoursewareFromPreparation(
				"prep-1",
				new ResourceCopyRequest("offering-2", "越界课件", "PRIVATE", null, null, null),
				authentication));
		verifyNoInteractions(offerings, coursewares);
	}

	@Test
	void rejectsCollectivePreparationWithoutTwoAcceptedMembersAndAgenda() {
		var service = service();
		var preparation = new Preparation();
		preparation.setId("prep-collective");
		preparation.setOfferingId("offering-1");
		preparation.setPreparationType("COLLECTIVE");
		when(preparations.findById("prep-collective")).thenReturn(Optional.of(preparation));
		when(authentication.getName()).thenReturn("teacher-1");
		when(scopes.resolve("teacher-1"))
				.thenReturn(new EducationDataScope(true, java.util.Set.of(), java.util.Set.of(),
						java.util.Set.of(), java.util.Set.of()));
		when(members.findByPreparationId("prep-collective")).thenReturn(java.util.List.of());

		assertThrows(IllegalStateException.class,
				() -> service.submitPreparation("prep-collective", authentication));
		verifyNoInteractions(reviews);
	}

	@Test
	void submitsBoundDraftVersionAndStartsReviewInOneServiceCommand() {
		var service = service();
		var courseware = new Courseware();
		courseware.setId("courseware-1");
		courseware.setOfferingId("offering-1");
		var version = new CoursewareVersion();
		version.setId("version-1");
		version.setCoursewareId(courseware.getId());
		version.setStatus("DRAFT");
		version.setBindState("BOUND");
		when(authentication.getName()).thenReturn("teacher-1");
		when(coursewareVersions.findById(version.getId())).thenReturn(Optional.of(version));
		when(coursewares.findById(courseware.getId())).thenReturn(Optional.of(courseware));

		service.submitVersionReview(true, version.getId(), authentication);

		assertEquals("SUBMITTED", version.getStatus());
		verify(coursewareVersions).save(version);
		verify(reviews).submit(
				eq("COURSEWARE"),
				eq(courseware.getId()),
				eq(courseware.getOfferingId()),
				argThat(value -> version.getId().equals(value.get("versionId"))),
				eq(authentication));
	}

	@Test
	void rejectsDirectSubmitTransitionWithoutStartingWorkflow() {
		var service = service();
		var courseware = new Courseware();
		courseware.setId("courseware-1");
		courseware.setOfferingId("offering-1");
		var version = new CoursewareVersion();
		version.setId("version-1");
		version.setCoursewareId(courseware.getId());
		version.setStatus("DRAFT");
		when(authentication.getName()).thenReturn("teacher-1");
		when(coursewareVersions.findById(version.getId())).thenReturn(Optional.of(version));
		when(coursewares.findById(courseware.getId())).thenReturn(Optional.of(courseware));

		assertThrows(
				IllegalStateException.class,
				() -> service.transition(true, version.getId(), "submit", authentication));

		verify(coursewareVersions, never()).save(any());
		verifyNoInteractions(reviews);
	}

	private TeachingCollaborationService service() {
		var service = new TeachingCollaborationService(
				preparations, members, prepMaterials, comments, coursewares, coursewareVersions,
				materials, materialVersions, offerings, schedules, scopes, files, managedFiles, reviews,
				identities, notifications);
		ReflectionTestUtils.setField(service, "em", entityManager);
		return service;
	}
}
