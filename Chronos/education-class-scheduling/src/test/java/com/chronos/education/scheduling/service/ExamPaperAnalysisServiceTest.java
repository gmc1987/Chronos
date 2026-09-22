package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.chronos.education.scheduling.dao.QuestionKnowledgePointRepository;
import com.chronos.education.scheduling.dao.QuestionRepository;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.Question;
import com.chronos.education.scheduling.model.QuestionKnowledgePoint;
import java.math.BigDecimal;
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
	void addItemPersistsStableQuestionReference() {
		ExamSession session = session("DRAFT");
		Question question = publishedQuestion();
		QuestionKnowledgePoint mapping = new QuestionKnowledgePoint();
		mapping.setQuestionId("question-1");
		mapping.setKnowledgePointId("knowledge-1");
		when(sessions.findById("session-1")).thenReturn(Optional.of(session));
		when(items.findBySessionIdOrderByQuestionNoAsc("session-1")).thenReturn(List.of());
		when(questions.findById("question-1")).thenReturn(Optional.of(question));
		when(questionKnowledgePoints.findByQuestionId("question-1")).thenReturn(List.of(mapping));
		when(items.save(any(ExamPaperItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ExamPaperItem result = service.addItem(
				"session-1",
				new ExamPaperAnalysisService.ItemCommand(
						"1",
						"question-1",
						"第一题",
						BigDecimal.TEN));

		assertThat(result.getQuestionId()).isEqualTo("question-1");
		assertThat(result.getTitle()).isEqualTo("第一题");
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
