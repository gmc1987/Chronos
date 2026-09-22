package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import java.nio.charset.StandardCharsets;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamItemScoreRepository;
import com.chronos.education.scheduling.dao.ExamPaperItemRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.ExamSessionOfferingRepository;
import com.chronos.education.scheduling.dao.QuestionKnowledgePointRepository;
import com.chronos.education.scheduling.dao.QuestionRepository;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.grade.dto.GradeSourceEventContracts.ExamScoresConfirmedV1;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.WrongAnswerConfirmed;

import lombok.RequiredArgsConstructor;

/**
 * 试卷分析使用场次下真实考生的逐题得分。未录入成绩时仅展示覆盖率，
 * 绝不将缺考或未批改考生当作零分参与平均值计算。
 */
@Service
@RequiredArgsConstructor
public class ExamPaperAnalysisService {
	private final ExamSessionRepository sessions;
	private final ExamSessionOfferingRepository sessionOfferings;
	private final ExamRoomRepository rooms;
	private final ExamCandidateRepository candidates;
	private final ExamPaperItemRepository items;
	private final ExamItemScoreRepository scores;
	private final ExamPlanRepository plans;
	private final EducationDomainEventService domainEvents;
	private final QuestionRepository questions;
	private final QuestionKnowledgePointRepository questionKnowledgePoints;

	/**
	 * 保留原有单元测试和独立调用方使用的构造入口。生产环境由 Spring 注入包含
	 * ExamSessionOfferingRepository 的完整构造器；缺少映射仓储时禁止确认成绩。
	 */
	ExamPaperAnalysisService(
			ExamSessionRepository sessions,
			ExamRoomRepository rooms,
			ExamCandidateRepository candidates,
			ExamPaperItemRepository items,
			ExamItemScoreRepository scores,
			ExamPlanRepository plans,
			EducationDomainEventService domainEvents,
			QuestionRepository questions,
			QuestionKnowledgePointRepository questionKnowledgePoints) {
		this.sessions = sessions;
		this.sessionOfferings = null;
		this.rooms = rooms;
		this.candidates = candidates;
		this.items = items;
		this.scores = scores;
		this.plans = plans;
		this.domainEvents = domainEvents;
		this.questions = questions;
		this.questionKnowledgePoints = questionKnowledgePoints;
	}

	@Transactional(readOnly = true)
	public List<ExamPaperItem> items(String sessionId) {
		requireSession(sessionId);
		return items.findBySessionIdOrderByQuestionNoAsc(sessionId);
	}

	@Transactional(readOnly = true)
	public List<AvailableQuestion> availableQuestions() {
		// 考试中心只暴露组卷所需快照字段，避免考务角色依赖教学中心管理权限。
		return questions.findByStatusAndArchivedFalseOrderByIdDesc("PUBLISHED").stream()
				.filter(question -> questionKnowledgePoints.existsByQuestionId(question.getId()))
				.map(question -> new AvailableQuestion(
						question.getId(),
						question.getStem(),
						question.getScore(),
						question.getQuestionType(),
						question.getDifficulty()))
				.toList();
	}

	@Transactional
	public ExamPaperItem addItem(String sessionId, ItemCommand command) {
		requireDraftScores(sessionId);
		if (command == null || blank(command.questionNo()) || blank(command.title())
				|| command.maxScore() == null || command.maxScore().signum() <= 0
				|| command.maxScore().scale() > 2) {
			throw new IllegalArgumentException("请填写题号、题目和有效分值");
		}
		if (items.findBySessionIdOrderByQuestionNoAsc(sessionId).stream()
				.anyMatch(item -> item.getQuestionNo().equals(command.questionNo().trim()))) {
			throw new IllegalArgumentException("同一场次题号不能重复");
		}
		if (blank(command.questionId())) {
			throw new IllegalArgumentException("请选择已发布的题库题目，才能形成知识点分析链路");
		}
		var question = questions.findById(command.questionId())
				.orElseThrow(() -> new IllegalArgumentException("题库题目不存在"));
		if (!"PUBLISHED".equals(question.getStatus()) || question.isArchived()) {
			throw new IllegalArgumentException("只能引用已发布且未归档的题库题目");
		}
		if (questionKnowledgePoints.findByQuestionId(question.getId()).isEmpty()) {
			throw new IllegalArgumentException("所选题目尚未关联知识点");
		}
		ExamPaperItem item = new ExamPaperItem();
		item.setSessionId(sessionId);
		item.setQuestionNo(command.questionNo().trim());
		item.setQuestionId(question.getId());
		item.setTitle(command.title().trim());
		item.setMaxScore(command.maxScore());
		return items.save(item);
	}

	@Transactional
	public void deleteItem(String sessionId, String itemId) {
		requireDraftScores(sessionId);
		ExamPaperItem item = requireItem(sessionId, itemId);
		if (scores.existsByItemId(itemId)) {
			throw new IllegalArgumentException("该题已有评分，不能删除");
		}
		items.delete(item);
	}

	@Transactional(readOnly = true)
	public List<ExamItemScore> itemScores(String sessionId, String itemId) {
		requireItem(sessionId, itemId);
		return scores.findByItemId(itemId);
	}

	@Transactional
	public ExamItemScore saveScore(String sessionId, String itemId, ScoreCommand command) {
		requireDraftScores(sessionId);
		ExamPaperItem item = requireItem(sessionId, itemId);
		if (!"PUBLISHED".equals(sessions.findById(sessionId).orElseThrow().getStatus())) {
			throw new IllegalStateException("考试发布后才能录入逐题成绩");
		}
		if (command == null || blank(command.candidateId()) || command.score() == null
				|| command.score().signum() < 0
				|| command.score().compareTo(item.getMaxScore()) > 0
				|| command.score().scale() > 2) {
			throw new IllegalArgumentException("逐题得分必须在 0 和题目满分之间");
		}
		ExamCandidate candidate = candidates.findById(command.candidateId())
				.orElseThrow(() -> new IllegalArgumentException("考生不存在"));
		boolean inSession = rooms.findBySessionId(sessionId).stream()
				.anyMatch(room -> Objects.equals(room.getId(), candidate.getRoomId()));
		if (!inSession) {
			throw new IllegalArgumentException("该考生不属于此考试场次");
		}
		ExamItemScore value = scores.findByItemIdAndCandidateId(itemId, candidate.getId())
				.orElseGet(ExamItemScore::new);
		value.setItemId(itemId);
		value.setCandidateId(candidate.getId());
		value.setScore(command.score());
		return scores.save(value);
	}

	@Transactional
	public com.chronos.education.scheduling.model.ExamSession confirmScores(String sessionId) {
		return confirmScores(sessionId, "SYSTEM");
	}

	@Transactional
	public com.chronos.education.scheduling.model.ExamSession confirmScores(
			String sessionId,
			String actor) {
		var session = requireSessionEntity(sessionId);
		if ("CONFIRMED".equals(session.getScoreStatus()) || "PUBLISHED".equals(session.getScoreStatus())) {
			return session;
		}
		List<ExamPaperItem> paperItems = items.findBySessionIdOrderByQuestionNoAsc(sessionId);
		if (paperItems.isEmpty()) {
			throw new IllegalStateException("请先维护试卷题目并完成评分");
		}
		long candidateCount = candidatesForSession(sessionId).size();
		if (candidateCount == 0) {
			throw new IllegalStateException("当前场次没有考生，不能确认成绩");
		}
		for (ExamPaperItem item : paperItems) {
			if (scores.findByItemId(item.getId()).size() != candidateCount) {
				throw new IllegalStateException("所有题目必须完成全部考生评分后才能确认");
			}
		}
		if (sessionOfferings == null) {
			throw new IllegalStateException("考试场次映射存储不可用，不能确认成绩");
		}
		List<String> offeringIds = sessionOfferings.findBySessionId(sessionId).stream()
				.map(mapping -> mapping.getOfferingId())
				.distinct()
				.toList();
		if (offeringIds.isEmpty()) {
			throw new IllegalStateException("考试场次缺少课程开设映射，不能确认成绩");
		}
		var confirmedAt = java.time.OffsetDateTime.now();
		var plan = plans.findById(session.getPlanId())
				.orElseThrow(() -> new IllegalStateException("考试计划不存在"));
		for (ExamCandidate candidate : candidatesForSession(sessionId)) {
			BigDecimal rawScore = paperItems.stream()
					.map(item -> scores.findByItemIdAndCandidateId(item.getId(), candidate.getId())
							.orElseThrow(() -> new IllegalStateException("考生逐题成绩不存在"))
							.getScore())
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal maxScore = paperItems.stream()
					.map(ExamPaperItem::getMaxScore)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			for (String offeringId : offeringIds) {
				ExamScoresConfirmedV1 event = new ExamScoresConfirmedV1(
						"EXAM_SCORES_CONFIRMED:" + sessionId + ":" + offeringId + ":" + candidate.getStudentId(),
						"ExamScoresConfirmedV1",
						confirmedAt,
						1,
						plan.getId(),
						sessionId,
						offeringId,
						candidate.getStudentId(),
						rawScore,
						maxScore,
						null,
						confirmedAt);
				domainEvents.enqueueGradeEvent(
						event.eventType(),
						sessionId,
						event.eventId(),
						event,
						actor);
			}
		}
		// 确认动作冻结逐题得分，避免审核、统计与错题沉淀读取到不同版本。
		session.setScoreStatus("CONFIRMED");
		session.setScoresConfirmedAt(java.time.LocalDateTime.now());
		return sessions.save(session);
	}

	@Transactional
	public com.chronos.education.scheduling.model.ExamSession publishScores(
			String sessionId,
			Authentication authentication) {
		var session = requireSessionEntity(sessionId);
		if ("PUBLISHED".equals(session.getScoreStatus())) {
			return session;
		}
		if (!"CONFIRMED".equals(session.getScoreStatus())) {
			throw new IllegalStateException("逐题成绩确认后才能发布");
		}
		var plan = plans.findById(session.getPlanId())
				.orElseThrow(() -> new IllegalStateException("考试计划不存在"));
		for (ExamPaperItem item : items.findBySessionIdOrderByQuestionNoAsc(sessionId)) {
			for (ExamItemScore score : scores.findByItemId(item.getId())) {
				if (score.getScore().compareTo(item.getMaxScore()) >= 0) {
					continue;
				}
				ExamCandidate candidate = candidates.findById(score.getCandidateId())
						.orElseThrow(() -> new IllegalStateException("逐题成绩关联考生不存在"));
				String sourceItemId = UUID.nameUUIDFromBytes((item.getId() + ":" + candidate.getId())
						.getBytes(StandardCharsets.UTF_8)).toString();
				// 同一场次同一考生同一题使用稳定来源键，重复发布不会重复生成错题。
				domainEvents.enqueueWrongAnswer(
						new WrongAnswerConfirmed(
								"EXAM_SCORE_PUBLISHED:" + sourceItemId,
								candidate.getStudentId(),
								session.getSubjectId(),
								plan.getSemesterCode(),
								null,
								sourceItemId,
								sessionId,
								item.getTitle(),
								"EXAM",
								null,
								java.time.LocalDateTime.now()),
						authentication.getName());
			}
		}
		// 错题事实与发布状态在同一事务提交，任一写入失败都会整体回滚。
		session.setScoreStatus("PUBLISHED");
		session.setScoresPublishedAt(java.time.LocalDateTime.now());
		return sessions.save(session);
	}

	@Transactional(readOnly = true)
	public List<ItemAnalysis> analysis(String sessionId) {
		requireSession(sessionId);
		long candidateCount = rooms.findBySessionId(sessionId).stream()
				.mapToLong(room -> candidates.findByRoomIdOrderBySeatNoAsc(room.getId()).size())
				.sum();
		return items.findBySessionIdOrderByQuestionNoAsc(sessionId).stream()
				.map(item -> summarize(item, candidateCount))
				.toList();
	}

	@Transactional(readOnly = true)
	public boolean hasItems(String sessionId) {
		return !items.findBySessionIdOrderByQuestionNoAsc(sessionId).isEmpty();
	}

	@Transactional(readOnly = true)
	public boolean hasScoresForRoom(String roomId) {
		return candidates.findByRoomIdOrderBySeatNoAsc(roomId).stream()
				.anyMatch(candidate -> scores.existsByCandidateId(candidate.getId()));
	}

	private ItemAnalysis summarize(ExamPaperItem item, long candidateCount) {
		List<ExamItemScore> values = scores.findByItemId(item.getId());
		long gradedCount = values.size();
		BigDecimal total = values.stream()
				.map(ExamItemScore::getScore)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal average = gradedCount == 0 ? null
				: total.divide(BigDecimal.valueOf(gradedCount), 2, RoundingMode.HALF_UP);
		BigDecimal scoreRate = average == null ? null
				: average.multiply(BigDecimal.valueOf(100))
						.divide(item.getMaxScore(), 2, RoundingMode.HALF_UP);
		long fullScoreCount = values.stream()
				.filter(value -> value.getScore().compareTo(item.getMaxScore()) == 0)
				.count();
		long zeroScoreCount = values.stream()
				.filter(value -> value.getScore().signum() == 0)
				.count();
		return new ItemAnalysis(item.getId(), item.getQuestionNo(), item.getTitle(),
				item.getMaxScore(), candidateCount, gradedCount, average,
				scoreRate, fullScoreCount, zeroScoreCount);
	}

	private ExamPaperItem requireItem(String sessionId, String itemId) {
		ExamPaperItem item = items.findById(itemId)
				.orElseThrow(() -> new IllegalArgumentException("题目不存在"));
		if (!item.getSessionId().equals(sessionId)) {
			throw new IllegalArgumentException("题目不属于此考试场次");
		}
		return item;
	}

	private void requireSession(String sessionId) {
		requireSessionEntity(sessionId);
	}

	private com.chronos.education.scheduling.model.ExamSession requireSessionEntity(String sessionId) {
		return sessions.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("考试场次不存在"));
	}

	private void requireDraftScores(String sessionId) {
		if (!"DRAFT".equals(requireSessionEntity(sessionId).getScoreStatus())) {
			throw new IllegalStateException("逐题成绩已确认，不能继续修改");
		}
	}

	private List<ExamCandidate> candidatesForSession(String sessionId) {
		return rooms.findBySessionId(sessionId).stream()
				.flatMap(room -> candidates.findByRoomIdOrderBySeatNoAsc(room.getId()).stream())
				.toList();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public record ItemCommand(String questionNo, String questionId, String title, BigDecimal maxScore) {
	}

	public record AvailableQuestion(
			String id,
			String stem,
			BigDecimal score,
			String questionType,
			String difficulty) {
	}

	public record ScoreCommand(String candidateId, BigDecimal score) {
	}

	public record ItemAnalysis(
			String itemId,
			String questionNo,
			String title,
			BigDecimal maxScore,
			long candidateCount,
			long gradedCount,
			BigDecimal averageScore,
			BigDecimal scoreRate,
			long fullScoreCount,
			long zeroScoreCount) {
	}
}
