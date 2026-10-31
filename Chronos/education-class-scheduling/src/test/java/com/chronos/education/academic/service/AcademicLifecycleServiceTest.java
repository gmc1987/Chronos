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
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(registrations.existsByTermCodeAndStudentIdAndStatus(
				"2026-2027-1", "student-1", "REGISTERED")).thenReturn(false);

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
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(registrations.existsByTermCodeAndStudentIdAndStatus(
				"2026-2027-1", "student-1", "REGISTERED")).thenReturn(true);
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
