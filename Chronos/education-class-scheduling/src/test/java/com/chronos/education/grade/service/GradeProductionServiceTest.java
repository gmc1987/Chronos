package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.education.grade.dao.AssessmentSchemeRepository;
import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradeChangeRequestRepository;
import com.chronos.education.grade.dao.GradePublishSnapshotRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.GradebookStudentRepository;
import com.chronos.education.grade.dao.MakeupExamRecordRepository;
import com.chronos.education.grade.dto.GradeProductionDtos.ChangeRequestCommand;
import com.chronos.education.grade.model.CourseGrade;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GradeProductionServiceTest {
	@Mock
	private GradeChangeRequestRepository changes;
	@Mock
	private MakeupExamRecordRepository makeups;
	@Mock
	private CourseGradeRepository courseGrades;
	@Mock
	private GradebookRepository gradebooks;
	@Mock
	private GradebookStudentRepository gradebookStudents;
	@Mock
	private AssessmentSchemeRepository schemes;
	@Mock
	private GradePublishSnapshotRepository snapshots;
	@Mock
	private CourseOfferingRepository offerings;
	@Mock
	private EducationDataScopeService dataScopes;
	@Mock
	private WorkflowService workflows;
	@Mock
	private IWorkflowTaskRepository workflowTasks;
	@Mock
	private DomainEventOutboxService domainEvents;
	@Mock
	private IAuditLogService audit;
	@Mock
	private ObjectMapper json;
	@Mock
	private ApplicationEventPublisher events;

	@InjectMocks
	private GradeProductionService service;

	@Test
	void rejectsChangeBasedOnHistoricalGradeVersion() {
		Gradebook gradebook = gradebook();
		CourseGrade historical = grade("grade-v1", 1);
		CourseGrade latest = grade("grade-v2", 2);
		stubAccess(gradebook, historical, List.of(latest, historical));

		assertThatThrownBy(() -> service.requestChange(
				"gradebook-1",
				new ChangeRequestCommand("grade-v1", new BigDecimal("85"), "修正录入错误"),
				"teacher"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("已有新版本");
	}

	@Test
	void rejectsSecondReviewingChangeForSamePublishedGrade() {
		Gradebook gradebook = gradebook();
		CourseGrade latest = grade("grade-v2", 2);
		stubAccess(gradebook, latest, List.of(latest));
		when(changes.existsByCourseGradeIdAndStatus("grade-v2", "REVIEWING"))
			.thenReturn(true);

		assertThatThrownBy(() -> service.requestChange(
				"gradebook-1",
				new ChangeRequestCommand("grade-v2", new BigDecimal("85"), "修正录入错误"),
				"teacher"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("待审核的更正申请");
	}

	@Test
	void rejectsIncidentReplayWithoutCompletedWorkflow() {
		var request = new com.chronos.education.grade.model.GradeChangeRequest();
		request.setId("change-1");
		request.setStatus("REVIEWING");
		when(changes.findByIdForUpdate("change-1")).thenReturn(Optional.of(request));

		assertThatThrownBy(() -> service.replayApprovedChange("change-1", "admin"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("已完成审批");
	}

	private void stubAccess(
			Gradebook gradebook,
			CourseGrade source,
			List<CourseGrade> versions) {
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
			false,
			Set.of(),
			Set.of(),
			Set.of(),
			Set.of("teacher-1"),
			Set.of()));
		when(courseGrades.findById(source.getId())).thenReturn(Optional.of(source));
		when(courseGrades.findByGradebookIdAndStudentIdOrderByVersionNoDesc(
			"gradebook-1",
			"student-1"))
			.thenReturn(versions);
	}

	private Gradebook gradebook() {
		Gradebook gradebook = new Gradebook();
		gradebook.setId("gradebook-1");
		gradebook.setTeacherId("teacher-1");
		return gradebook;
	}

	private CourseGrade grade(String id, int version) {
		CourseGrade grade = new CourseGrade();
		grade.setId(id);
		grade.setGradebookId("gradebook-1");
		grade.setStudentId("student-1");
		grade.setVersionNo(version);
		grade.setTotalScore(new BigDecimal("80"));
		return grade;
	}
}
