package com.chronos.education.academic.service;

import static com.chronos.education.academic.dto.AcademicLifecycleDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chronos.education.academic.dao.*;
import com.chronos.education.academic.model.*;
import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AcademicLifecycleServiceTest {
	@Mock AcademicProgramRepository programs;
	@Mock AcademicProgramCourseRepository programCourses;
	@Mock TermRegistrationRepository registrations;
	@Mock CourseEnrollmentRepository enrollments;
	@Mock GraduationAuditRepository graduationAudits;
	@Mock MajorRepository majors;
	@Mock CourseCatalogRepository courses;
	@Mock AcademicTermRepository terms;
	@Mock StudentProfileRepository students;
	@Mock CourseOfferingRepository offerings;
	@Mock TeachingClassMemberRepository members;
	@Mock GradebookRepository gradebooks;
	@Mock CourseGradeRepository courseGrades;
	@Mock EducationDataScopeService dataScopes;
	@Mock IAuditLogService audit;
	AcademicLifecycleService service;

	@BeforeEach
	void setUp() {
		service = new AcademicLifecycleService(programs, programCourses, registrations, enrollments,
				graduationAudits, majors, courses, terms, students, offerings, members, gradebooks,
				courseGrades, dataScopes, audit, new ObjectMapper());
		when(dataScopes.resolve(anyString())).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
	}

	@Test
	void programSubmitterCannotPublishOwnProgram() {
		AcademicProgram program = program("SUBMITTED");
		program.setSubmittedBy("author");
		when(programs.findForUpdate("program-1")).thenReturn(Optional.of(program));

		assertThrows(AccessDeniedException.class,
				() -> service.publishProgram("program-1", "author"));
		verify(programs, never()).save(any());
	}

	@Test
	void graduationRejectsProgramFromDifferentMajorOrCohort() {
		AcademicProgram program = program("PUBLISHED");
		program.setMajorId("major-1");
		program.setCohortYear(2026);
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setMajorId("major-1");
		student.setGradeYear(2024);
		when(students.findById("student-1")).thenReturn(Optional.of(student));
		when(programs.findById("program-1")).thenReturn(Optional.of(program));

		GraduationAuditCommand command = new GraduationAuditCommand("school-1", "student-1", "program-1");
		assertThrows(IllegalStateException.class, () -> service.evaluateGraduation(command, "operator"));
		student.setGradeYear(2026);
		student.setMajorId("major-2");
		assertThrows(IllegalStateException.class, () -> service.evaluateGraduation(command, "operator"));
		verifyNoInteractions(programCourses, graduationAudits);
	}

	@Test
	void registrationSubmitterCannotApproveOwnRecord() {
		TermRegistration registration = new TermRegistration();
		registration.setId("registration-1");
		registration.setSchoolId("school-1");
		registration.setStatus("SUBMITTED");
		registration.setCreateBy("author");
		when(registrations.findForUpdate("registration-1")).thenReturn(Optional.of(registration));

		assertThrows(AccessDeniedException.class, () -> service.decideRegistration(
				"registration-1", new RegistrationDecisionCommand(true, ""), "author"));
		verify(registrations, never()).save(any());
	}

	@Test
	void enrollmentRequiresConfirmedTermRegistration() {
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setSemesterCode("2026-2027-1");
		offering.setStatus("ACTIVE");
		offering.setCampusId("school-1");
		mockEnrollmentScope();
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(registrations.findBySchoolIdAndTermCodeAndStudentId(
				"school-1", "2026-2027-1", "student-1")).thenReturn(Optional.empty());

		EnrollmentCommand command = new EnrollmentCommand(
				"school-1", "2026-2027-1", "student-1", "offering-1", null, "MANUAL");
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> service.enroll(command, "operator"));
		assertEquals("学生尚未完成本学期注册", error.getMessage());
		verify(members, never()).save(any());
		verify(enrollments, never()).save(any());
	}

	@Test
	void enrollmentProjectionUsesSupportedManualSource() {
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setSemesterCode("2026-2027-1");
		offering.setStatus("ACTIVE");
		offering.setCampusId("school-1");
		mockEnrollmentScope();
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		var registration = new TermRegistration();
		registration.setStatus("REGISTERED");
		registration.setProgramId("program-1");
		when(registrations.findBySchoolIdAndTermCodeAndStudentId(
				"school-1", "2026-2027-1", "student-1")).thenReturn(Optional.of(registration));
		when(enrollments.findByTermCodeAndStudentIdAndOfferingId(
				"2026-2027-1", "student-1", "offering-1")).thenReturn(Optional.empty());
		when(members.findByOfferingIdAndStudentId("offering-1", "student-1"))
				.thenReturn(Optional.empty());

		service.enroll(new EnrollmentCommand(
				"school-1", "2026-2027-1", "student-1", "offering-1", null, "AGENT"), "operator");

		var member = org.mockito.ArgumentCaptor.forClass(TeachingClassMember.class);
		verify(members).save(member.capture());
		assertEquals("MANUAL", member.getValue().getEnrollmentSource());
		assertEquals("ENROLLED", member.getValue().getEnrollmentStatus());
	}

	private void mockEnrollmentScope() {
		var student = new StudentProfile();
		student.setId("student-1");
		var term = new com.chronos.education.scheduling.model.AcademicTerm();
		term.setOrganizationId("school-1");
		when(students.findById("student-1")).thenReturn(Optional.of(student));
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		when(dataScopes.scheduleOrganizationCampuses(any(), org.mockito.ArgumentMatchers.eq("school-1")))
				.thenReturn(Set.of("school-1"));
	}

	private AcademicProgram program(String status) {
		AcademicProgram program = new AcademicProgram();
		program.setId("program-1");
		program.setSchoolId("school-1");
		program.setMajorId("major-1");
		program.setCohortYear(2026);
		program.setEducationLevel("VOCATIONAL");
		program.setProgramCode("HV-2026-CS");
		program.setProgramName("计算机应用技术 2026 级培养方案");
		program.setStatus(status);
		return program;
	}
}
