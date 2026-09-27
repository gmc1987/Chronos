package com.chronos.education.classgroup.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.classgroup.dao.*;
import com.chronos.education.classgroup.model.ClassGroup;
import com.chronos.education.homeschool.dao.ParentAccountBindingRepository;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ClassGroupServiceTest {
	@Mock ClassGroupRepository groups;
	@Mock ClassGroupMemberRepository members;
	@Mock ClassGroupAuditRepository audits;
	@Mock AdministrativeClassRepository classes;
	@Mock StudentProfileRepository students;
	@Mock StudentGuardianRepository guardians;
	@Mock ParentAccountBindingRepository bindings;
	@Mock EducationDataScopeService scopes;
	@Mock IAuditLogService audit;
	@InjectMocks ClassGroupService service;

	@Test
	void createsOnlyForVisibleClassAndWritesAudit() {
		AdministrativeClass clazz = new AdministrativeClass();
		clazz.setId("class-1");
		clazz.setClassName("一班");
		EducationDataScope scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(scopes.resolve("admin")).thenReturn(scope);
		when(scopes.requireSchoolForCampus(scope, null)).thenReturn("school-1");
		when(classes.findById("class-1")).thenReturn(Optional.of(clazz));
		when(groups.findByClassId("class-1")).thenReturn(Optional.empty());
		when(groups.save(any(ClassGroup.class))).thenAnswer(invocation -> {
			ClassGroup value = invocation.getArgument(0);
			value.setId("group-1");
			return value;
		});
		when(audits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.create("admin", "class-1", "一班家长群", "create-1");

		verify(audits).save(any());
		verify(audit).log(eq("admin"), eq("EDU_CLASS_GROUP_GROUP_CREATE"), any());
	}

	@Test
	void rejectsCrossClassAccessBeforeReadingGroupMembers() {
		ClassGroup group = new ClassGroup();
		group.setId("group-1");
		group.setClassId("class-2");
		when(groups.findById("group-1")).thenReturn(Optional.of(group));
		AdministrativeClass clazz = new AdministrativeClass();
		clazz.setId("class-2");
		clazz.setHeadTeacherId("teacher-2");
		when(classes.findById("class-2")).thenReturn(Optional.of(clazz));
		when(scopes.resolve("teacher")).thenReturn(new EducationDataScope(false,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));

		assertThrows(AccessDeniedException.class, () -> service.members("teacher", "group-1", 0, 20));
		verifyNoInteractions(members);
	}
}
