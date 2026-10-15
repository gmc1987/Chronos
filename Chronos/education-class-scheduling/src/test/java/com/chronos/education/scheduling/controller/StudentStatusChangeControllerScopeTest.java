package com.chronos.education.scheduling.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentStatusChange;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.StudentStatusChangeService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class StudentStatusChangeControllerScopeTest {
	private final StudentStatusChangeService changes = mock(StudentStatusChangeService.class);
	private final EducationDataScopeService scopes = mock(EducationDataScopeService.class);
	private final Authentication authentication = mock(Authentication.class);
	private final EducationDataScope schoolScope = new EducationDataScope(
			false, Set.of("school-1"), Set.of("school-1"), Set.of(),
			Set.of("class-1"), Set.of(), Set.of());
	private final StudentStatusChangeController controller = new StudentStatusChangeController(changes, scopes);

	@BeforeEach
	void setUp() {
		when(authentication.getName()).thenReturn("school.approver");
		when(scopes.resolve("school.approver")).thenReturn(schoolScope);
	}

	@Test
	void schoolApproverCanApproveItsOwnStudentWithoutGlobalScope() {
		StudentStatusChange change = change("class-1");
		when(changes.find("change-1")).thenReturn(change);

		controller.approve("change-1", null, authentication);

		verify(scopes).assertStudentAccess(schoolScope, "student-1");
		verify(changes).approve("change-1", null, "school.approver");
	}

	@Test
	void schoolApproverCannotApproveTransferIntoAnotherScope() {
		when(changes.find("change-1")).thenReturn(change("class-2"));
		doThrow(new AccessDeniedException("无权访问该班级"))
				.when(scopes).assertClassAccess(schoolScope, "class-2");

		assertThatThrownBy(() -> controller.approve("change-1", null, authentication))
				.isInstanceOf(AccessDeniedException.class);
		verify(scopes).assertStudentAccess(schoolScope, "student-1");
	}

	@Test
	void schoolApproverCannotCancelAnotherSchoolsStudentChange() {
		when(changes.find("change-1")).thenReturn(change("class-1"));
		doThrow(new AccessDeniedException("无权访问该学生数据"))
				.when(scopes).assertStudentAccess(schoolScope, "student-1");

		assertThatThrownBy(() -> controller.cancel("change-1", authentication))
				.isInstanceOf(AccessDeniedException.class);
	}

	private StudentStatusChange change(String targetClassId) {
		StudentStatusChange change = new StudentStatusChange();
		change.setStudentId("student-1");
		change.setFromClassId("class-1");
		change.setToClassId(targetClassId);
		return change;
	}
}
