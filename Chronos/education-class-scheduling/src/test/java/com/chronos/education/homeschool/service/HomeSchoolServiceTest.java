package com.chronos.education.homeschool.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.chronos.education.homeschool.dao.HomeNoticeRepository;
import com.chronos.education.homeschool.dao.HomeNoticeTargetRepository;
import com.chronos.education.homeschool.dao.ParentAccountBindingRepository;
import com.chronos.education.homeschool.model.ParentAccountBinding;
import com.chronos.education.homeschool.dto.HomeSchoolDtos.ParentBindingCommand;
import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.grade.model.CourseGrade;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.homeschool.model.HomeNotice;
import com.chronos.education.homeschool.model.HomeNoticeTarget;
import com.chronos.education.homeschool.model.ParentAccountBinding;
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
import com.chronos.service.iService.IAuditLogService;

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
	@Mock IAuditLogService audit;
	@Mock DomainEventOutboxService domainEvents;
	@Mock GradeCenterService gradeCenter;
	@InjectMocks HomeSchoolService service;

	@Test
	void invalidatedBindingImmediatelyLosesPortalAccess() {
		when(bindings.findByUsernameAndStatus("parent@example.test", "ACTIVE"))
				.thenReturn(java.util.Optional.empty());

		assertThrows(AccessDeniedException.class,
				() -> service.children("parent@example.test"));
	}

	@Test
	void bindingWritesAuditEvent() {
		ParentProfile parent = new ParentProfile();
		parent.setId("parent-1");
		parent.setStatus("ACTIVE");
		ParentAccountBinding saved = new ParentAccountBinding();
		saved.setId("binding-1");
		saved.setParentId("parent-1");
		saved.setUsername("parent@example.test");
		saved.setStatus("ACTIVE");
		when(parents.findById("parent-1")).thenReturn(java.util.Optional.of(parent));
		when(bindings.findByUsername("parent@example.test")).thenReturn(java.util.Optional.empty());
		when(bindings.save(org.mockito.ArgumentMatchers.any())).thenReturn(saved);

		service.bind(new ParentBindingCommand("parent-1", "parent@example.test"));

		verify(audit).log("parent@example.test", "EDU_HOME_PARENT_BINDING_CREATE",
				"bindingId=binding-1,parentId=parent-1");
	}

	@Test
	void publishingNoticeAuditsAndEnqueuesDurableEvent() {
		HomeNotice notice = new HomeNotice();
		notice.setId("notice-1");
		notice.setClassId("class-1");
		notice.setStatus("DRAFT");
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setAdministrativeClassId("class-1");
		student.setEnrollmentStatus("ACTIVE");
		StudentGuardianRelation relation = new StudentGuardianRelation();
		relation.setStudentId("student-1");
		relation.setParentId("parent-1");
		when(notices.findById("notice-1")).thenReturn(java.util.Optional.of(notice));
		when(scopes.resolve("teacher")).thenReturn(new EducationDataScope(true, java.util.Set.of(),
				java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));
		when(students.findByAdministrativeClassId("class-1")).thenReturn(java.util.List.of(student));
		when(guardians.findByStudentIdOrderByCreateTime("student-1")).thenReturn(java.util.List.of(relation));
		ParentAccountBinding binding = new ParentAccountBinding();
		binding.setParentId("parent-1");
		when(bindings.findByParentIdInAndStatus(any(), eq("ACTIVE"))).thenReturn(java.util.List.of(binding));
		when(targets.save(any(HomeNoticeTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(notices.save(any(HomeNotice.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.publish("notice-1", "teacher");

		verify(audit).log(eq("teacher"), eq("EDU_HOME_NOTICE_PUBLISH"), any());
		verify(domainEvents).enqueue(eq("HomeNoticePublishedV1"), eq("notice-1"),
				eq("HOME_NOTICE_PUBLISHED:notice-1"), any());
	}

	@Test
	void familyGradesOnlyReadsPublishedGradesForActiveChildren() {
		ParentAccountBinding binding = new ParentAccountBinding();
		binding.setParentId("parent-1");
		ParentProfile parent = new ParentProfile();
		parent.setId("parent-1");
		parent.setStatus("ACTIVE");
		StudentGuardianRelation relation = new StudentGuardianRelation();
		relation.setParentId("parent-1");
		relation.setStudentId("student-1");
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setEnrollmentStatus("ACTIVE");
		CourseGrade grade = new CourseGrade();
		grade.setStudentId("student-1");
		when(bindings.findByUsernameAndStatus("parent@example.test", "ACTIVE")).thenReturn(java.util.Optional.of(binding));
		when(parents.findById("parent-1")).thenReturn(java.util.Optional.of(parent));
		when(guardians.findByParentIdOrderByCreateTime("parent-1")).thenReturn(java.util.List.of(relation));
		when(students.findById("student-1")).thenReturn(java.util.Optional.of(student));
		when(gradeCenter.studentGrades("student-1")).thenReturn(java.util.List.of(grade));

		var result = service.familyGrades("parent@example.test");

		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(grade), result);
		verify(gradeCenter).studentGrades("student-1");
		verify(audit).log(eq("parent@example.test"), eq("EDU_HOME_PARENT_GRADE_VIEW"), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void familyGradesExcludesInactiveChildren() {
		ParentAccountBinding binding = new ParentAccountBinding();
		binding.setParentId("parent-1");
		ParentProfile parent = new ParentProfile();
		parent.setId("parent-1");
		parent.setStatus("ACTIVE");
		StudentGuardianRelation relation = new StudentGuardianRelation();
		relation.setParentId("parent-1");
		relation.setStudentId("student-1");
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setEnrollmentStatus("WITHDRAWN");
		when(bindings.findByUsernameAndStatus("parent@example.test", "ACTIVE")).thenReturn(java.util.Optional.of(binding));
		when(parents.findById("parent-1")).thenReturn(java.util.Optional.of(parent));
		when(guardians.findByParentIdOrderByCreateTime("parent-1")).thenReturn(java.util.List.of(relation));
		when(students.findById("student-1")).thenReturn(java.util.Optional.of(student));

		org.junit.jupiter.api.Assertions.assertTrue(service.familyGrades("parent@example.test").isEmpty());
		org.mockito.Mockito.verifyNoInteractions(gradeCenter);
	}
}
