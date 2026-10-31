package com.chronos.education.internship.service;

import static com.chronos.education.internship.dto.InternshipDtos.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.internship.dao.InternshipActivityRepository;
import com.chronos.education.internship.dao.InternshipAssessmentRepository;
import com.chronos.education.internship.dao.InternshipCompanyRepository;
import com.chronos.education.internship.dao.InternshipIncidentRepository;
import com.chronos.education.internship.dao.InternshipPlacementRepository;
import com.chronos.education.internship.dao.InternshipPositionRepository;
import com.chronos.education.internship.model.InternshipAssessment;
import com.chronos.education.internship.model.InternshipCompany;
import com.chronos.education.internship.model.InternshipIncident;
import com.chronos.education.internship.model.InternshipPlacement;
import com.chronos.education.internship.model.InternshipPosition;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class InternshipServiceTest {
	@Mock InternshipCompanyRepository companies;
	@Mock InternshipPositionRepository positions;
	@Mock InternshipPlacementRepository placements;
	@Mock InternshipActivityRepository activities;
	@Mock InternshipAssessmentRepository assessments;
	@Mock InternshipIncidentRepository incidents;
	@Mock StudentProfileRepository students;
	@Mock AcademicTermRepository terms;
	@Mock EducationDataScopeService dataScopes;
	@Mock IAuditLogService audit;
	InternshipService service;

	@BeforeEach
	void setUp() {
		service = new InternshipService(companies, positions, placements, activities,
				assessments, incidents, students, terms, dataScopes, audit, new ObjectMapper());
		when(dataScopes.resolve(anyString())).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
	}

	@Test
	void companySubmitterCannotApproveOwnApplication() {
		InternshipCompany company = new InternshipCompany();
		company.setId("company-1");
		company.setSchoolId("school-1");
		company.setStatus("SUBMITTED");
		company.setSubmittedBy("author");
		when(companies.findForUpdate("company-1")).thenReturn(Optional.of(company));

		assertThrows(AccessDeniedException.class,
				() -> service.decideCompany("company-1", new DecisionCommand(true, ""), "author"));
		verify(companies, never()).save(any());
	}

	@Test
	void placementRequiresAgreementConsentAndInsurance() {
		AcademicTerm term = new AcademicTerm();
		StudentProfile student = new StudentProfile();
		student.setEnrollmentStatus("ACTIVE");
		InternshipCompany company = new InternshipCompany();
		company.setId("company-1");
		company.setSchoolId("school-1");
		company.setStatus("APPROVED");
		InternshipPosition position = new InternshipPosition();
		position.setId("position-1");
		position.setCompanyId("company-1");
		position.setStatus("ACTIVE");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		when(students.findById("student-1")).thenReturn(Optional.of(student));
		when(companies.findById("company-1")).thenReturn(Optional.of(company));
		when(positions.findById("position-1")).thenReturn(Optional.of(position));
		PlacementCommand command = new PlacementCommand(
				"school-1", "2026-2027-1", "student-1", "company-1", "position-1",
				LocalDate.of(2027, 2, 1), LocalDate.of(2027, 6, 30), "", "consent", "insurance");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.createPlacement(command, "operator"));
		assertEquals("必须绑定实习协议", error.getMessage());
	}

	@Test
	void assessmentSubmitterCannotApproveOwnAssessment() {
		InternshipAssessment assessment = new InternshipAssessment();
		assessment.setId("assessment-1");
		assessment.setPlacementId("placement-1");
		assessment.setSubmittedBy("author");
		assessment.setStatus("SUBMITTED");
		InternshipPlacement placement = placement("ACTIVE");
		when(assessments.findForUpdate("assessment-1")).thenReturn(Optional.of(assessment));
		when(placements.findById("placement-1")).thenReturn(Optional.of(placement));

		assertThrows(AccessDeniedException.class,
				() -> service.decideAssessment("assessment-1", new DecisionCommand(true, ""), "author"));
		verify(assessments, never()).save(any());
	}

	@Test
	void incidentReporterCannotCloseOwnIncident() {
		InternshipIncident incident = new InternshipIncident();
		incident.setId("incident-1");
		incident.setPlacementId("placement-1");
		incident.setReportedBy("reporter");
		incident.setStatus("INVESTIGATING");
		when(incidents.findForUpdate("incident-1")).thenReturn(Optional.of(incident));
		when(placements.findById("placement-1")).thenReturn(Optional.of(placement("ACTIVE")));

		assertThrows(AccessDeniedException.class,
				() -> service.closeIncident("incident-1", new ClosureCommand("已完成调查"), "reporter"));
		verify(incidents, never()).save(any());
	}

	private InternshipPlacement placement(String status) {
		InternshipPlacement placement = new InternshipPlacement();
		placement.setId("placement-1");
		placement.setSchoolId("school-1");
		placement.setStatus(status);
		return placement;
	}
}
