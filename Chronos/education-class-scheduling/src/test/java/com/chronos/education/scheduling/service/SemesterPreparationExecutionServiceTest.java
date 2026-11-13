package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.AdmissionCandidate;
import com.chronos.education.scheduling.model.CourseCatalog;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherTeachingAssignment;
import java.util.Optional;
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

	@Test
	void explicitCourseDisambiguatesSubjectWithMultipleEnabledCourses() {
		var classes = mock(AdministrativeClassRepository.class);
		var students = mock(StudentProfileRepository.class);
		var terms = mock(AcademicTermRepository.class);
		var assignments = mock(TeacherTeachingAssignmentRepository.class);
		var courses = mock(CourseCatalogRepository.class);
		var teachers = mock(TeacherAcademicProfileRepository.class);
		var service = new SemesterPreparationExecutionService(mock(AdmissionCandidateRepository.class),
				classes, students, mock(AdmissionService.class), terms, assignments, courses,
				mock(CourseOfferingRepository.class), teachers, mock(ClassSchedulingService.class),
				mock(AcademicDataService.class), mock(org.springframework.jdbc.core.JdbcTemplate.class),
				mock(com.chronos.education.academic.service.TermCoursePlanService.class));
		var term = new AcademicTerm();
		term.setId("term");
		term.setOrganizationId("school");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		var assignment = new TeacherTeachingAssignment();
		assignment.setId("assignment");
		assignment.setAcademicTermId("term");
		assignment.setSubjectId("math");
		assignment.setAdministrativeClassId("class");
		assignment.setTeacherId("teacher");
		when(assignments.findAllByOrderByCreateTimeDesc()).thenReturn(List.of(assignment));
		var classGroup = new AdministrativeClass();
		classGroup.setId("class");
		classGroup.setCampusId("school");
		classGroup.setClassName("一班");
		when(classes.findById("class")).thenReturn(Optional.of(classGroup));
		var student = new StudentProfile();
		student.setEnrollmentStatus("ACTIVE");
		when(students.findByAdministrativeClassId("class")).thenReturn(List.of(student));
		var teacher = new TeacherAcademicProfile();
		teacher.setTeacherName("张老师");
		when(teachers.findById("teacher")).thenReturn(Optional.of(teacher));
		when(courses.findAll()).thenReturn(List.of(course("MATH01", "高等数学 A"), course("MATH02", "线性代数")));
		assertThat(service.generateOfferings("2026-2027-1", 100, true, "school"))
				.containsEntry("ambiguousCourse", 1);
		assignment.setCourseId("MATH01");
		var result = service.generateOfferings("2026-2027-1", 100, true, "school");
		assertThat(result).containsEntry("ambiguousCourse", 0).containsEntry("creatable", 1);
		assertThat(result.get("proposals").toString()).contains("高等数学 A").doesNotContain("线性代数");
	}

	private CourseCatalog course(String code, String name) {
		var course = new CourseCatalog();
		course.setId(code);
		course.setCourseCode(code);
		course.setCourseName(name);
		course.setSubjectId("math");
		course.setOrganizationId("school");
		return course;
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
				mock(org.springframework.jdbc.core.JdbcTemplate.class),
				mock(com.chronos.education.academic.service.TermCoursePlanService.class));
	}
}
