package com.chronos.education.scheduling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamItemScoreRepository;
import com.chronos.education.scheduling.dao.ExamPaperItemRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;

import lombok.RequiredArgsConstructor;

/**
 * 试卷分析使用场次下真实考生的逐题得分。未录入成绩时仅展示覆盖率，
 * 绝不将缺考或未批改考生当作零分参与平均值计算。
 */
@Service
@RequiredArgsConstructor
public class ExamPaperAnalysisService {
	private final ExamSessionRepository sessions;
	private final ExamRoomRepository rooms;
	private final ExamCandidateRepository candidates;
	private final ExamPaperItemRepository items;
	private final ExamItemScoreRepository scores;

	@Transactional(readOnly = true)
	public List<ExamPaperItem> items(String sessionId) {
		requireSession(sessionId);
		return items.findBySessionIdOrderByQuestionNoAsc(sessionId);
	}

	@Transactional
	public ExamPaperItem addItem(String sessionId, ItemCommand command) {
		requireSession(sessionId);
		if (command == null || blank(command.questionNo()) || blank(command.title())
				|| command.maxScore() == null || command.maxScore().signum() <= 0
				|| command.maxScore().scale() > 2) {
			throw new IllegalArgumentException("请填写题号、题目和有效分值");
		}
		if (items.findBySessionIdOrderByQuestionNoAsc(sessionId).stream()
				.anyMatch(item -> item.getQuestionNo().equals(command.questionNo().trim()))) {
			throw new IllegalArgumentException("同一场次题号不能重复");
		}
		ExamPaperItem item = new ExamPaperItem();
		item.setSessionId(sessionId);
		item.setQuestionNo(command.questionNo().trim());
		item.setTitle(command.title().trim());
		item.setMaxScore(command.maxScore());
		return items.save(item);
	}

	@Transactional
	public void deleteItem(String sessionId, String itemId) {
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
		if (!sessions.existsById(sessionId)) {
			throw new IllegalArgumentException("考试场次不存在");
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public record ItemCommand(String questionNo, String title, BigDecimal maxScore) {
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
