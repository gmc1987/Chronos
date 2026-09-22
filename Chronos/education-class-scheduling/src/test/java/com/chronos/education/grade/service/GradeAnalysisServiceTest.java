package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.GradebookStudentRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationGradeRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamItemScoreRepository;
import com.chronos.education.scheduling.dao.ExamPaperItemRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.KnowledgePointRepository;
import com.chronos.education.scheduling.dao.QuestionKnowledgePointRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.KnowledgePoint;
import com.chronos.education.scheduling.model.QuestionKnowledgePoint;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradeAnalysisServiceTest {
	@Mock
	private CourseGradeRepository courseGrades;
	@Mock
	private GradebookRepository gradebooks;
	@Mock
	private GradebookStudentRepository gradebookStudents;
	@Mock
	private CourseOfferingRepository offerings;
	@Mock
	private AdministrativeClassRepository classes;
	@Mock
	private EducationGradeRepository grades;
	@Mock
	private EducationDataScopeService dataScopes;
	@Mock
	private ExamPaperItemRepository examItems;
	@Mock
	private ExamItemScoreRepository examScores;
	@Mock
	private ExamCandidateRepository examCandidates;
	@Mock
	private ExamSessionRepository examSessions;
	@Mock
	private ExamPlanRepository examPlans;
	@Mock
	private QuestionKnowledgePointRepository questionKnowledgePoints;
	@Mock
	private KnowledgePointRepository knowledgePoints;
	@Mock
	private StudentProfileRepository studentProfiles;
	@Mock
	private SubjectRepository subjects;

	@InjectMocks
	private GradeAnalysisService service;

	@Test
	void knowledgeAnalysisAppliesSemesterClassGradeAndSubjectFilters() {
		EducationDataScope scope = new EducationDataScope(
				true,
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of());
		when(dataScopes.resolve("analyst")).thenReturn(scope);
		when(dataScopes.canAccessStudent(scope, "student-1")).thenReturn(true);

		ExamPaperItem item = new ExamPaperItem();
		item.setId("item-1");
		item.setSessionId("session-1");
		item.setQuestionId("question-1");
		item.setMaxScore(BigDecimal.TEN);
		when(examItems.findAll()).thenReturn(List.of(item));

		ExamSession session = new ExamSession();
		session.setId("session-1");
		session.setPlanId("plan-1");
		session.setSubjectId("subject-1");
		session.setScoreStatus("PUBLISHED");
		when(examSessions.findAll()).thenReturn(List.of(session));

		ExamPlan plan = new ExamPlan();
		plan.setId("plan-1");
		plan.setSemesterCode("2026-1");
		when(examPlans.findAll()).thenReturn(List.of(plan));

		Subject subject = new Subject();
		subject.setId("subject-1");
		subject.setSubjectCode("MATH");
		when(subjects.findAll()).thenReturn(List.of(subject));

		ExamCandidate candidate = new ExamCandidate();
		candidate.setId("candidate-1");
		candidate.setStudentId("student-1");
		when(examCandidates.findAll()).thenReturn(List.of(candidate));

		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setAdministrativeClassId("class-1");
		student.setGradeId("grade-1");
		when(studentProfiles.findAll()).thenReturn(List.of(student));

		ExamItemScore score = new ExamItemScore();
		score.setCandidateId("candidate-1");
		score.setScore(new BigDecimal("8"));
		when(examScores.findByItemId("item-1")).thenReturn(List.of(score));

		QuestionKnowledgePoint mapping = new QuestionKnowledgePoint();
		mapping.setQuestionId("question-1");
		mapping.setKnowledgePointId("point-1");
		when(questionKnowledgePoints.findByQuestionId("question-1")).thenReturn(List.of(mapping));

		KnowledgePoint point = new KnowledgePoint();
		point.setId("point-1");
		point.setName("一元一次方程");
		point.setEnabled(true);
		when(knowledgePoints.findAll()).thenReturn(List.of(point));

		var result = service.knowledge(
				"2026-1",
				"class-1",
				"grade-1",
				"MATH",
				"analyst");

		assertThat(result.available()).isTrue();
		assertThat(result.groups()).singleElement().satisfies(metric -> {
			assertThat(metric.name()).isEqualTo("一元一次方程");
			assertThat(metric.averageScore()).isEqualByComparingTo("80.00");
			assertThat(metric.studentCount()).isEqualTo(1);
		});

		var excluded = service.knowledge(
				"2026-2",
				"class-1",
				"grade-1",
				"MATH",
				"analyst");
		assertThat(excluded.available()).isFalse();
		assertThat(excluded.groups()).isEmpty();
	}
}
