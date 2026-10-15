package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamItemScoreRepository;
import com.chronos.education.scheduling.dao.ExamPaperItemRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.ExamSessionOfferingRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.dao.QuestionKnowledgePointRepository;
import com.chronos.education.scheduling.dao.QuestionRepository;
import com.chronos.education.scheduling.dao.QuestionVersionRepository;
import com.chronos.education.scheduling.dao.QuestionBankRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.ExamSessionOffering;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.Question;
import com.chronos.education.scheduling.model.QuestionVersion;
import com.chronos.education.scheduling.model.QuestionBank;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.QuestionKnowledgePoint;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ExamPaperAnalysisServiceTest {
	private ExamSessionRepository sessions;
	private ExamRoomRepository rooms;
	private ExamCandidateRepository candidates;
	private ExamPaperItemRepository items;
	private ExamItemScoreRepository scores;
	private ExamPlanRepository plans;
	private EducationDomainEventService domainEvents;
	private QuestionRepository questions;
	private QuestionKnowledgePointRepository questionKnowledgePoints;
	private ExamPaperAnalysisService service;

	@BeforeEach
	void setUp() {
		sessions = mock(ExamSessionRepository.class);
		rooms = mock(ExamRoomRepository.class);
		candidates = mock(ExamCandidateRepository.class);
		items = mock(ExamPaperItemRepository.class);
		scores = mock(ExamItemScoreRepository.class);
		plans = mock(ExamPlanRepository.class);
		domainEvents = mock(EducationDomainEventService.class);
		questions = mock(QuestionRepository.class);
		questionKnowledgePoints = mock(QuestionKnowledgePointRepository.class);
		service = new ExamPaperAnalysisService(
				sessions,
				rooms,
				candidates,
				items,
				scores,
				plans,
				domainEvents,
				questions,
				questionKnowledgePoints);
	}

	@Test
	void confirmationRequiresEveryCandidateScore() {
		ExamSession session = session("DRAFT");
		ExamPaperItem item = item();
		ExamRoom room = room();
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(items.findBySessionIdOrderByQuestionNoAsc("session-1")).thenReturn(List.of(item));
		when(rooms.findBySessionId("session-1")).thenReturn(List.of(room));
		when(candidates.findByRoomIdOrderBySeatNoAsc("room-1"))
				.thenReturn(List.of(candidate("candidate-1"), candidate("candidate-2")));
		when(scores.findByItemId("item-1")).thenReturn(List.of(score("candidate-1", "8")));

		assertThatThrownBy(() -> service.confirmScores("session-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("所有题目必须完成全部考生评分后才能确认");
	}

	@Test
	void batchScoringAcceptsTwoCandidatesAndRejectsDuplicates() {
		ExamSession session = session("DRAFT");
		ExamPaperItem item = item();
		ExamRoom room = room();
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		when(rooms.findBySessionId("session-1")).thenReturn(List.of(room));
		when(candidates.findById("candidate-1")).thenReturn(Optional.of(candidate("candidate-1")));
		when(candidates.findById("candidate-2")).thenReturn(Optional.of(candidate("candidate-2")));
		when(scores.findByItemId("item-1")).thenReturn(List.of(score("candidate-1", "8"),
				score("candidate-2", "9")));
		when(scores.save(any(ExamItemScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<ExamItemScore> saved = service.saveScoresBatch("session-1", "item-1", List.of(
				new ExamPaperAnalysisService.ScoreCommand("candidate-1", new BigDecimal("8")),
				new ExamPaperAnalysisService.ScoreCommand("candidate-2", new BigDecimal("9"))));
		assertThat(saved).hasSize(2);
		verify(scores, times(2)).save(any(ExamItemScore.class));
		assertThatThrownBy(() -> service.saveScoresBatch("session-1", "item-1", List.of(
				new ExamPaperAnalysisService.ScoreCommand("candidate-1", new BigDecimal("8")),
				new ExamPaperAnalysisService.ScoreCommand("candidate-1", new BigDecimal("9")))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("批量成绩含空考生或重复考生");
	}

	@Test
	void confirmationSendsOnlyTheCandidatesEnrolledOffering() {
		ExamSessionOfferingRepository mappings = mock(ExamSessionOfferingRepository.class);
		TeachingClassMemberRepository members = mock(TeachingClassMemberRepository.class);
		service = new ExamPaperAnalysisService(sessions, mappings, members,
				rooms, candidates, items, scores, plans, domainEvents,
				questions, mock(QuestionVersionRepository.class), mock(QuestionBankRepository.class),
				mock(CourseOfferingRepository.class), questionKnowledgePoints);
		ExamSession session = session("DRAFT");
		ExamCandidate candidate = candidate("candidate-1");
		ExamPlan plan = new ExamPlan();
		plan.setId("plan-1");
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(items.findBySessionIdOrderByQuestionNoAsc("session-1"))
				.thenReturn(List.of(item()));
		when(rooms.findBySessionId("session-1")).thenReturn(List.of(room()));
		when(candidates.findByRoomIdOrderBySeatNoAsc("room-1"))
				.thenReturn(List.of(candidate));
		when(scores.findByItemId("item-1"))
				.thenReturn(List.of(score("candidate-1", "8")));
		when(scores.findByItemIdAndCandidateId("item-1", "candidate-1"))
				.thenReturn(Optional.of(score("candidate-1", "8")));
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
		ExamSessionOffering first = new ExamSessionOffering();
		first.setOfferingId("offering-1");
		ExamSessionOffering second = new ExamSessionOffering();
		second.setOfferingId("offering-2");
		when(mappings.findBySessionId("session-1"))
				.thenReturn(List.of(first, second));
		TeachingClassMember enrollment = new TeachingClassMember();
		enrollment.setStudentId(candidate.getStudentId());
		enrollment.setOfferingId("offering-1");
		when(members.findByOfferingIdInAndEnrollmentStatus(
				List.of("offering-1", "offering-2"), "ENROLLED"))
				.thenReturn(List.of(enrollment));

		service.confirmScores("session-1");

		verify(domainEvents, times(1)).enqueueGradeEvent(
				eq("ExamScoresConfirmedV1"), eq("session-1"), any(), any(), any());
		assertThat(session.getScoreStatus()).isEqualTo("CONFIRMED");
	}

	@Test
	void addItemRejectsQuestionWithoutKnowledgePoint() {
		ExamSession session = session("DRAFT");
		Question question = publishedQuestion();
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(items.findBySessionIdOrderByQuestionNoAsc("session-1")).thenReturn(List.of());
		when(questions.findById("question-1")).thenReturn(Optional.of(question));
		when(questionKnowledgePoints.findByQuestionId("question-1")).thenReturn(List.of());

		assertThatThrownBy(() -> service.addItem(
				"session-1",
				new ExamPaperAnalysisService.ItemCommand(
						"1",
						"question-1",
						"第一题",
						BigDecimal.TEN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("所选题目尚未关联知识点");
	}

	@Test
	void addItemPersistsStableQuestionReference() throws Exception {
		QuestionVersionRepository versions = mock(QuestionVersionRepository.class);
		ExamSessionOfferingRepository mappings = mock(ExamSessionOfferingRepository.class);
		QuestionBankRepository banks = mock(QuestionBankRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		service = new ExamPaperAnalysisService(sessions, mappings,
				mock(TeachingClassMemberRepository.class), rooms, candidates, items, scores,
				plans, domainEvents, questions, versions, banks, offerings, questionKnowledgePoints);
		ExamSession session = session("DRAFT");
		Question question = publishedQuestion();
		QuestionBank bank = new QuestionBank();
		bank.setCourseId("MATH-G1");
		CourseOffering offering = new CourseOffering();
		offering.setCourseCode("MATH-G1");
		ExamSessionOffering mapping = new ExamSessionOffering();
		mapping.setOfferingId("offering-1");
		QuestionVersion version = new QuestionVersion();
		version.setId("version-1");
		version.setQuestionId("question-1");
		version.setStatus("PUBLISHED");
		version.setSnapshotJson("{\"stem\":\"第一题\"}");
		version.setSnapshotHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(version.getSnapshotJson().getBytes(StandardCharsets.UTF_8))));
		QuestionKnowledgePoint knowledgeMapping = new QuestionKnowledgePoint();
		knowledgeMapping.setQuestionId("question-1");
		knowledgeMapping.setKnowledgePointId("knowledge-1");
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(items.findBySessionIdOrderByQuestionNoAsc("session-1")).thenReturn(List.of());
		when(questions.findById("question-1")).thenReturn(Optional.of(question));
		when(banks.findById("bank-1")).thenReturn(Optional.of(bank));
		when(mappings.findBySessionId("session-1")).thenReturn(List.of(mapping));
		when(offerings.findAllById(any())).thenReturn(List.of(offering));
		when(versions.findById("version-1")).thenReturn(Optional.of(version));
		when(questionKnowledgePoints.findByQuestionId("question-1")).thenReturn(List.of(knowledgeMapping));
		when(items.save(any(ExamPaperItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ExamPaperItem result = service.addItem(
				"session-1",
				new ExamPaperAnalysisService.ItemCommand(
						"1",
						"question-1",
						"第一题",
						BigDecimal.TEN));

		assertThat(result.getQuestionId()).isEqualTo("question-1");
		assertThat(result.getQuestionVersionId()).isEqualTo("version-1");
		assertThat(result.getQuestionSnapshotHash()).isEqualTo(version.getSnapshotHash());
		assertThat(result.getTitle()).isEqualTo("第一题");
		assertThatThrownBy(() -> service.addItem(
				"session-1",
				new ExamPaperAnalysisService.ItemCommand(
						"2", "question-1", "未经发布的改写题干", BigDecimal.TEN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("试卷题目必须与题库已发布版本一致");
		bank.setCourseId("OTHER-COURSE");
		assertThatThrownBy(() -> service.addItem(
				"session-1",
				new ExamPaperAnalysisService.ItemCommand(
						"2", "question-1", "第一题", BigDecimal.TEN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("题目不属于本场考试关联的课程");
		bank.setCourseId("MATH-G1");
		bank.setOfferingId("other-offering");
		assertThatThrownBy(() -> service.addItem(
				"session-1",
				new ExamPaperAnalysisService.ItemCommand(
						"2", "question-1", "第一题", BigDecimal.TEN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("题目不属于本场考试关联的课程");
	}

	@Test
	void publicationCreatesOnlyWrongAnswerFactsAndIsIdempotent() {
		ExamSession session = session("CONFIRMED");
		ExamPaperItem item = item();
		ExamCandidate wrong = candidate("candidate-1");
		ExamCandidate full = candidate("candidate-2");
		ExamPlan plan = new ExamPlan();
		plan.setSemesterCode("2026-1");
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
		when(items.findBySessionIdOrderByQuestionNoAsc("session-1")).thenReturn(List.of(item));
		when(scores.findByItemId("item-1")).thenReturn(List.of(
				score("candidate-1", "8"),
				score("candidate-2", "10")));
		when(candidates.findById("candidate-1")).thenReturn(Optional.of(wrong));
		when(sessions.save(session)).thenReturn(session);

		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("exam-admin");
		service.publishScores("session-1", authentication);

		assertThat(session.getScoreStatus()).isEqualTo("PUBLISHED");
		verify(domainEvents, times(1)).enqueueWrongAnswer(any(), any());

		service.publishScores("session-1", authentication);
		verify(domainEvents, times(1)).enqueueWrongAnswer(any(), any());
	}

	private ExamSession session(String scoreStatus) {
		ExamSession value = new ExamSession();
		value.setId("session-1");
		value.setPlanId("plan-1");
		value.setSubjectId("subject-1");
		value.setStatus("PUBLISHED");
		value.setScoreStatus(scoreStatus);
		return value;
	}

	private Question publishedQuestion() {
		Question value = new Question();
		value.setId("question-1");
		value.setStatus("PUBLISHED");
		value.setArchived(false);
		value.setPublishedVersionId("version-1");
		value.setBankId("bank-1");
		return value;
	}

	private ExamPaperItem item() {
		ExamPaperItem value = new ExamPaperItem();
		value.setId("item-1");
		value.setSessionId("session-1");
		value.setTitle("第一题");
		value.setMaxScore(BigDecimal.TEN);
		return value;
	}

	private ExamRoom room() {
		ExamRoom value = new ExamRoom();
		value.setId("room-1");
		value.setSessionId("session-1");
		return value;
	}

	private ExamCandidate candidate(String id) {
		ExamCandidate value = new ExamCandidate();
		value.setId(id);
		value.setStudentId("student-" + id);
		value.setRoomId("room-1");
		return value;
	}

	private ExamItemScore score(String candidateId, String score) {
		ExamItemScore value = new ExamItemScore();
		value.setCandidateId(candidateId);
		value.setItemId("item-1");
		value.setScore(new BigDecimal(score));
		return value;
	}
}
