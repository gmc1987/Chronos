package com.chronos.education.homeschool.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.chronos.education.homeschool.dao.HomeNoticeRepository;
import com.chronos.education.homeschool.dao.HomeNoticeTargetRepository;
import com.chronos.education.homeschool.dao.ParentAccountBindingRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class HomeSchoolServiceTest {
	@Mock ParentAccountBindingRepository bindings;
	@Mock HomeNoticeRepository notices;
	@Mock HomeNoticeTargetRepository targets;
	@Mock ParentProfileRepository parents;
	@Mock StudentProfileRepository students;
	@Mock StudentGuardianRepository guardians;
	@Mock AdministrativeClassRepository classes;
	@Mock EducationDataScopeService scopes;
	@InjectMocks HomeSchoolService service;

	@Test
	void invalidatedBindingImmediatelyLosesPortalAccess() {
		when(bindings.findByUsernameAndStatus("parent@example.test", "ACTIVE"))
				.thenReturn(java.util.Optional.empty());

		assertThrows(AccessDeniedException.class,
				() -> service.children("parent@example.test"));
	}
}
