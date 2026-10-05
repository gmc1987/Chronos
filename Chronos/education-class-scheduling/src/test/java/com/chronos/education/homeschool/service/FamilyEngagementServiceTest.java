package com.chronos.education.homeschool.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.homeschool.dao.*;
import com.chronos.education.homeschool.dto.FamilyEngagementDtos.*;
import com.chronos.education.homeschool.model.*;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.file.service.ManagedFileService;
import com.chronos.service.iService.IAuditLogService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class FamilyEngagementServiceTest {
	@Mock ParentFeedbackRepository feedbacks;
	@Mock CommunicationRecordRepository communications;
	@Mock EducationUserBindingRepository bindings;
	@Mock ParentProfileRepository parents;
	@Mock StudentProfileRepository students;
	@Mock StudentGuardianRepository guardians;
	@Mock AdministrativeClassRepository classes;
	@Mock EducationDataScopeService scopes;
	@Mock ManagedFileService files;
	@Mock IAuditLogService audit;
	@InjectMocks FamilyEngagementService service;

	@Test
	void parentCannotSubmitForUnrelatedStudent() {
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileId("parent-1");
		when(bindings.findByUsernameAndProfileTypeAndStatus("parent", "PARENT", "ACTIVE")).thenReturn(Optional.of(binding));
		ParentProfile parent = new ParentProfile();
		parent.setStatus("ACTIVE");
		when(parents.findById("parent-1")).thenReturn(Optional.of(parent));
		when(guardians.findByParentIdOrderByCreateTime("parent-1")).thenReturn(java.util.List.of());

		assertThrows(AccessDeniedException.class,
				() -> service.submit(new FeedbackCommand("student-2", "x", "y", null, null), "parent"));
	}

	@Test
	void invalidFeedbackTransitionIsRejected() {
		ParentFeedback value = new ParentFeedback();
		value.setId("feedback-1");
		value.setStudentId("student-1");
		value.setStatus("SUBMITTED");
		when(feedbacks.findById("feedback-1")).thenReturn(Optional.of(value));
		when(bindings.findByUsernameAndProfileTypeAndStatus("teacher", "PARENT", "ACTIVE")).thenReturn(Optional.empty());
		when(scopes.resolve("teacher")).thenReturn(new EducationDataScope(true, java.util.Set.of(),
				java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));

		assertThrows(IllegalStateException.class, () -> service.transition("feedback-1",
				new FeedbackActionCommand("RESOLVED", null, null), "teacher"));
	}

	@Test
	void parentCanConfirmResolvedFeedback() {
		EducationUserBinding binding = new EducationUserBinding();
		binding.setProfileId("parent-1");
		when(bindings.findByUsernameAndProfileTypeAndStatus("parent", "PARENT", "ACTIVE")).thenReturn(Optional.of(binding));
		ParentProfile parent = new ParentProfile();
		parent.setStatus("ACTIVE");
		when(parents.findById("parent-1")).thenReturn(Optional.of(parent));
		ParentFeedback value = new ParentFeedback();
		value.setId("feedback-1");
		value.setParentId("parent-1");
		value.setStatus("RESOLVED");
		when(feedbacks.findById("feedback-1")).thenReturn(Optional.of(value));
		when(feedbacks.save(any(ParentFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

		FeedbackResponse result = service.parentConfirm("feedback-1", false, "parent");

		assertEquals("CLOSED", result.status());
		assertNotNull(result.parentConfirmedAt());
	}
}
