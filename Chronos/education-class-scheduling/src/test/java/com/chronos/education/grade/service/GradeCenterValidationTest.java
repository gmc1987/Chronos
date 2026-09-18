package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chronos.education.grade.dao.AssessmentComponentRepository;
import com.chronos.education.grade.dao.AssessmentSchemeRepository;
import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradeItemRepository;
import com.chronos.education.grade.dao.GradePublishSnapshotRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.GradebookStudentRepository;
import com.chronos.education.grade.dto.GradeDtos.ComponentCommand;
import com.chronos.education.grade.dto.GradeDtos.SchemeCommand;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import java.math.BigDecimal;
import java.util.List;
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
						BigDecimal.valueOf(60), BigDecimal.valueOf(100), 1)));

		assertThrows(IllegalArgumentException.class, () -> service.createScheme(command, "teacher"));
	}

	@Test
	void rejectsNonManualSourcesInFirstSlice() {
		SchemeCommand command = new SchemeCommand(
				"offering-1", "方案", BigDecimal.valueOf(100), BigDecimal.valueOf(60),
				List.of(new ComponentCommand("exam", "考试", "EXAM",
						BigDecimal.valueOf(100), BigDecimal.valueOf(100), 1)));

		assertThrows(IllegalArgumentException.class, () -> service.createScheme(command, "teacher"));
	}
}
