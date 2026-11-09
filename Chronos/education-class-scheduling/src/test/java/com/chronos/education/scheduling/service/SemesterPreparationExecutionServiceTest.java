package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.AdmissionCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemesterPreparationExecutionServiceTest {
	@Test
	void formalReviewCannotExpandAttachedRosterToOtherStudentsInSameYear() {
		var repository = mock(AdmissionCandidateRepository.class);
		var admissions = mock(AdmissionService.class);
		var selected = candidate("selected", "TEST-1");
		var unrelated = candidate("unrelated", "TEST-2");
		when(repository.findAll()).thenReturn(List.of(selected, unrelated));
		var service = service(repository, admissions);
		var result = service.reviewAdmissions(2026, 100, false, "admin", "school-1", List.of("TEST-1"));
		assertThat(result).containsEntry("candidateCount", 1).containsEntry("succeeded", 1);
		verify(admissions).review(eq("selected"), any(), eq("admin"));
		verify(admissions, never()).review(eq("unrelated"), any(), any());
	}

	@Test
	void emptyRosterScopeReviewsNobodyInsteadOfEntireYear() {
		var repository = mock(AdmissionCandidateRepository.class);
		var admissions = mock(AdmissionService.class);
		when(repository.findAll()).thenReturn(List.of(candidate("unrelated", "TEST-2")));
		var result = service(repository, admissions).reviewAdmissions(2026, 100, false, "admin", "school-1", List.of());
		assertThat(result).containsEntry("candidateCount", 0).containsEntry("succeeded", 0);
		verify(admissions, never()).review(any(), any(), any());
	}

	private AdmissionCandidate candidate(String id, String candidateNo) {
		var value = new AdmissionCandidate();
		value.setId(id);
		value.setCandidateNo(candidateNo);
		value.setStudentName("验收学生");
		value.setOrganizationId("school-1");
		value.setAdmissionYear(2026);
		value.setAdmissionStatus("ADMITTED");
		value.setAdmittedMajorId("major-1");
		value.setPlannedClassId("class-1");
		return value;
	}

	private SemesterPreparationExecutionService service(AdmissionCandidateRepository repository, AdmissionService admissions) {
		return new SemesterPreparationExecutionService(repository, mock(AdministrativeClassRepository.class),
				mock(StudentProfileRepository.class), admissions, mock(AcademicTermRepository.class),
				mock(TeacherTeachingAssignmentRepository.class), mock(CourseCatalogRepository.class),
				mock(CourseOfferingRepository.class), mock(TeacherAcademicProfileRepository.class),
				mock(ClassSchedulingService.class), mock(AcademicDataService.class),
				mock(org.springframework.jdbc.core.JdbcTemplate.class));
	}
}
