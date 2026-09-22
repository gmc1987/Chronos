package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.HomeworkAssignmentRepository;
import com.chronos.education.scheduling.dao.HomeworkSubmissionRepository;
import com.chronos.education.scheduling.dao.LessonPlanRepository;
import com.chronos.education.scheduling.dao.PreparationRepository;
import com.chronos.education.scheduling.dao.QuestionRepository;
import com.chronos.education.scheduling.dao.QuestionVersionRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.dao.TeachingPlanItemRepository;
import com.chronos.education.scheduling.dao.TeachingPlanRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.AssignmentRequest;

class HomeworkDeliveryContractTest {
	private HomeworkService service;
	private EducationDataScopeService scopes;
	private Authentication authentication;

	@BeforeEach
	void setUp() {
		scopes = mock(EducationDataScopeService.class);
		authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("teacher.one");
		EducationDataScope scope = new EducationDataScope(true, Set.of(), Set.of("teacher-1"), Set.of(), Set.of());
		when(scopes.resolve("teacher.one")).thenReturn(scope);
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		when(mockOfferings().findById("offering-1")).thenReturn(java.util.Optional.of(offering));
		service = new HomeworkService(
				mock(HomeworkAssignmentRepository.class),
				mock(HomeworkSubmissionRepository.class),
				mock(TeachingClassMemberRepository.class),
				mockOfferings(),
				scopes,
				mock(TeachingPlanItemRepository.class),
				mock(TeachingPlanRepository.class),
				mock(PreparationRepository.class),
				mock(LessonPlanRepository.class),
				mock(QuestionRepository.class),
				mock(QuestionVersionRepository.class),
				mock(EducationDomainEventService.class));
	}

	private CourseOfferingRepository mockOfferings() {
		return offerings;
	}

	private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);

	private AssignmentRequest request(String lateRule, String attachments) {
		return new AssignmentRequest("offering-1", "HOMEWORK", null, null, null, "作业",
				"[]", "[]", null, LocalDateTime.now().plusDays(1), null, 100, 1,
				"ALLOW".equals(lateRule), lateRule, "ENROLLED_STUDENTS", attachments);
	}

	@Test
	void rejectsUnsupportedLateStrategy() {
		assertThatThrownBy(() -> service.create(request("PENALIZE", "[]"), authentication))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("仅支持 REJECT 或 ALLOW");
	}

	@Test
	void rejectsMalformedAttachmentSnapshot() {
		assertThatThrownBy(() -> service.create(request("REJECT", "{}"), authentication))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("作业附件快照必须是 JSON 数组");
	}
}
