package com.chronos.education.scheduling.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.chronos.education.scheduling.service.AccommodationAssignmentImportService;
import com.chronos.education.scheduling.service.AccommodationImportService;
import com.chronos.education.scheduling.service.AccommodationService;
import com.chronos.education.scheduling.service.AccommodationService.AssignmentCommand;
import com.chronos.security.DirectoryDataScopeGuard;

class AccommodationControllerTest {
	private AccommodationService accommodations;
	private AccommodationAssignmentImportService assignmentImports;
	private DirectoryDataScopeGuard scopes;
	private AccommodationController controller;
	private Authentication authentication;

	@BeforeEach
	void setUp() {
		accommodations = mock(AccommodationService.class);
		assignmentImports = mock(AccommodationAssignmentImportService.class);
		scopes = mock(DirectoryDataScopeGuard.class);
		controller = new AccommodationController(accommodations, mock(AccommodationImportService.class),
				assignmentImports, scopes);
		authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("operator");
	}

	@Test
	void manualAssignmentRejectsCrossOrganizationIdsEvenWhenBothAreVisible() {
		when(accommodations.bedOrganization("bed-1")).thenReturn("org-1");
		when(accommodations.studentOrganization("student-1")).thenReturn("org-2");

		assertThatThrownBy(() -> controller.assign(
				new AssignmentCommand("ORG1:2026-1", "student-1", "bed-1"), authentication))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("学生与床位不属于同一机构");

		verify(scopes).requireOrganization(authentication, "org-1");
		verify(scopes).requireOrganization(authentication, "org-2");
		verifyNoInteractions(assignmentImports);
	}

	@Test
	void commitRechecksOrganizationScopeBeforeImportService() {
		var command = new AccommodationController.AssignmentImportCommit(
				"preview-1", "org-1", "term-1", "1.0", "key-1");
		when(scopes.resolveOrganization(authentication, "org-1")).thenReturn("org-1");

		controller.assignmentImportCommit(command, authentication);

		verify(scopes).resolveOrganization(authentication, "org-1");
		verify(assignmentImports).commit("preview-1", "org-1", "term-1", "1.0", "key-1", "operator");
	}
}
