package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.grade.dao.GradeChangeIncidentRepository;
import com.chronos.education.grade.event.GradeChangeApplyFailedEvent;
import com.chronos.education.grade.model.GradeChangeIncident;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class GradeChangeIncidentServiceTest {
	@Mock
	private GradeChangeIncidentRepository incidents;
	@Mock
	private EducationDataScopeService dataScopes;
	@Mock
	private IAuditLogService audit;

	@InjectMocks
	private GradeChangeIncidentService service;

	@Test
	void recordsFailureWithoutSensitivePayloadAndTruncatesTechnicalError() {
		when(incidents.findByChangeRequestId("change-1")).thenReturn(Optional.empty());
		when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		String oversizedError = "x".repeat(2500);

		service.recordFailure(new GradeChangeApplyFailedEvent(
			"change-1",
			"workflow-1",
			oversizedError));

		ArgumentCaptor<GradeChangeIncident> captor = ArgumentCaptor.forClass(GradeChangeIncident.class);
		verify(incidents).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
		assertThat(captor.getValue().getLastError()).hasSize(2000);
	}

	@Test
	void rejectsIncidentOperationsOutsideFullSchoolScope() {
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
			false,
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of("teacher-1"),
			Set.of()));

		assertThatThrownBy(() -> service.requireOpen("incident-1", "teacher"))
			.isInstanceOf(AccessDeniedException.class)
			.hasMessageContaining("全校数据范围");
	}
}
