package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.grade.dao.AssessmentComponentRepository;
import com.chronos.education.grade.dao.AssessmentSchemeRepository;
import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradeItemRepository;
import com.chronos.education.grade.dao.GradePublishSnapshotRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.GradebookStudentRepository;
import com.chronos.education.grade.dto.GradeDtos.ComponentCommand;
import com.chronos.education.grade.dto.GradeDtos.GradeItemCommand;
import com.chronos.education.grade.dto.GradeDtos.ItemsCommand;
import com.chronos.education.grade.dto.GradeDtos.SchemeCommand;
import com.chronos.education.grade.model.AssessmentComponent;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.model.GradebookStudent;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradeCenterValidationTest {
	@Mock AssessmentSchemeRepository schemes;
	@Mock AssessmentComponentRepository components;
	@Mock GradebookRepository gradebooks;
	@Mock GradebookStudentRepository students;
	@Mock GradeItemRepository items;
	@Mock CourseGradeRepository courseGrades;
	@Mock GradePublishSnapshotRepository snapshots;
	@Mock CourseOfferingRepository offerings;
	@Mock TeachingClassMemberRepository members;
	@Mock StudentProfileRepository profiles;
	@Mock WorkflowService workflows;
	@Mock IWorkflowTaskRepository workflowTasks;
	@Mock IWorkflowInstanceRepository workflowInstances;
	@Mock EducationDataScopeService dataScopes;
	@Mock GradeNotificationService notifications;
	@Mock IAuditLogService audit;
	@InjectMocks GradeCenterService service;

	@Test
	void rejectsWeightsThatDoNotTotalOneHundred() {
		SchemeCommand command = new SchemeCommand(
				"offering-1", "方案", BigDecimal.valueOf(100), BigDecimal.valueOf(60),
				List.of(new ComponentCommand("final", "期末", "MANUAL",
						BigDecimal.valueOf(60), BigDecimal.valueOf(100), 1)), null);

		assertThrows(IllegalArgumentException.class, () -> service.createScheme(command, "teacher"));
	}

	@Test
	void rejectsNonManualSourcesInFirstSlice() {
		SchemeCommand command = new SchemeCommand(
				"offering-1", "方案", BigDecimal.valueOf(100), BigDecimal.valueOf(60),
				List.of(new ComponentCommand("exam", "考试", "EXAM",
						BigDecimal.valueOf(100), BigDecimal.valueOf(100), 1)), null);

		assertThrows(IllegalArgumentException.class, () -> service.createScheme(command, "teacher"));
	}

	@Test
	void persistsGradebookWhenSavingItems() {
		Gradebook gradebook = new Gradebook();
		gradebook.setId("gradebook-1");
		gradebook.setSchemeId("scheme-1");
		gradebook.setTeacherId("teacher-1");
		gradebook.setStatus("EDITING");
		gradebook.setRowVersion(3L);
		AssessmentComponent component = new AssessmentComponent();
		component.setId("component-1");
		component.setMaxScore(BigDecimal.valueOf(100));
		GradebookStudent student = new GradebookStudent();
		student.setStudentId("student-1");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		when(components.findBySchemeIdOrderBySortOrder("scheme-1")).thenReturn(List.of(component));
		when(students.findByGradebookId("gradebook-1")).thenReturn(List.of(student));
		when(items.findByGradebookId("gradebook-1")).thenReturn(List.of());
		service.saveItems("gradebook-1",
				new ItemsCommand(List.of(new GradeItemCommand("component-1", "student-1",
						BigDecimal.valueOf(88), "NORMAL", null)), 3L),
				"teacher");
		verify(gradebooks).saveAndFlush(gradebook);
	}

	@Test
	void rejectsPublishingAlreadyPublishedGradebook() {
		Gradebook gradebook = new Gradebook();
		gradebook.setId("gradebook-1");
		gradebook.setStatus("PUBLISHED");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		assertThrows(IllegalStateException.class, () -> service.publish("gradebook-1", "publisher"));
	}
}
