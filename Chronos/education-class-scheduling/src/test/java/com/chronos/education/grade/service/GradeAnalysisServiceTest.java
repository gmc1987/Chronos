package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.chronos.education.grade.dao.*;
import com.chronos.education.grade.dto.GradeDtos.AnalysisResult;
import com.chronos.education.grade.model.*;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.EducationDomainEventService;
import com.chronos.workflow.WorkflowService;
import com.chronos.Idao.workflow.*;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GradeAnalysisServiceTest {
	@Test
	void classAnalysisUsesPublishedSnapshotGradesOnly() {
		Gradebook book = new Gradebook();
		book.setId("book-1");
		book.setOfferingId("offering-1");
		book.setSubmissionNo(2);
		book.setStatus("PUBLISHED");
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		offering.setCourseCode("MATH");
		offering.setSemesterCode("2026-SPRING");
		offering.setTeacherId("teacher-1");
		GradebookStudent student = new GradebookStudent();
		student.setStudentId("student-1");
		student.setAdministrativeClassId("class-1");
		CourseGrade grade = new CourseGrade();
		grade.setStudentId("student-1");
		grade.setTotalScore(new BigDecimal("86"));
		grade.setPassed(true);

		GradebookRepository gradebooks = mock(GradebookRepository.class);
		GradePublishSnapshotRepository snapshots = mock(GradePublishSnapshotRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		GradebookStudentRepository students = mock(GradebookStudentRepository.class);
		CourseGradeRepository courseGrades = mock(CourseGradeRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		when(gradebooks.findByStatusOrderByPublishedAtDesc("PUBLISHED")).thenReturn(List.of(book));
		when(snapshots.findByGradebookIdAndVersionNo("book-1", 2)).thenReturn(Optional.of(new GradePublishSnapshot()));
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(students.findByGradebookId("book-1")).thenReturn(List.of(student));
		when(courseGrades.findByGradebookIdAndVersionNo("book-1", 2)).thenReturn(List.of(grade));
		when(scopes.resolve("teacher-1")).thenReturn(new EducationDataScope(true, java.util.Set.of(),
				java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));

		GradeAnalysisServiceFixture fixture = new GradeAnalysisServiceFixture();
		AnalysisResult result = fixture.service(gradebooks, students, courseGrades, snapshots, offerings, scopes)
				.analysis("class", "teacher-1");

		assertThat(result.supported()).isTrue();
		assertThat(result.rows()).singleElement().satisfies(row -> {
			assertThat(row.key()).isEqualTo("class-1");
			assertThat(row.averageScore()).isEqualByComparingTo("86.00");
			assertThat(row.passRate()).isEqualByComparingTo("100.00");
		});
	}

	private static final class GradeAnalysisServiceFixture {
		private GradeCenterService service(GradebookRepository gradebooks, GradebookStudentRepository students,
				CourseGradeRepository courseGrades, GradePublishSnapshotRepository snapshots,
				CourseOfferingRepository offerings, EducationDataScopeService scopes) {
			return new GradeCenterService(mock(AssessmentSchemeRepository.class),
					mock(AssessmentComponentRepository.class), gradebooks, students, mock(GradeItemRepository.class),
					courseGrades, snapshots, mock(GradeCorrectionRepository.class), offerings,
					mock(TeachingClassMemberRepository.class),
					mock(StudentProfileRepository.class), mock(SubjectRepository.class), mock(WorkflowService.class),
					mock(IWorkflowTaskRepository.class), mock(IWorkflowInstanceRepository.class), scopes,
					mock(GradeNotificationService.class), mock(IAuditLogService.class), new ObjectMapper(),
					mock(DomainEventOutboxService.class), mock(EducationDomainEventService.class));
		}
	}

}
