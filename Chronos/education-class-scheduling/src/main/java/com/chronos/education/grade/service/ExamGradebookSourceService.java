package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.GradeItemRepository;
import com.chronos.education.grade.model.AssessmentComponent;
import com.chronos.education.grade.model.GradeItem;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.model.GradebookStudent;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.DataGradeEventFactRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamSessionOfferingRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.model.DataGradeEventFact;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.ExamSessionOffering;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Binds a gradebook component to one published exam session and imports its projected facts. */
@Service
@RequiredArgsConstructor
public class ExamGradebookSourceService {
	private static final String EXAM_EVENT = "ExamScoresConfirmedV1";
	private final CourseOfferingRepository offerings;
	private final ExamPlanRepository plans;
	private final ExamSessionRepository sessions;
	private final ExamSessionOfferingRepository mappings;
	private final DataGradeEventFactRepository facts;
	private final GradeItemRepository items;

	@Transactional(readOnly = true)
	public List<ExamSourceOption> availableSessions(String offeringId) {
		if (!offerings.existsById(offeringId)) {
			throw new IllegalArgumentException("课程开设不存在");
		}
		return mappings.findByOfferingId(offeringId).stream()
				.map(mapping -> sessions.findById(mapping.getSessionId()).orElse(null))
				.filter(session -> session != null && "PUBLISHED".equals(session.getStatus()))
				.map(session -> new ExamSourceOption(session.getId(), session.getExamDate(),
						session.getStartTime().toString(), session.getScoreStatus()))
				.sorted(Comparator.comparing(ExamSourceOption::examDate).reversed())
				.toList();
	}

	@Transactional(readOnly = true)
	public void validateBinding(String offeringId, String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("考试成绩项目必须选择来源场次");
		}
		var offering = offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("课程开设不存在"));
		ExamSession session = sessions.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("考试场次不存在"));
		var plan = plans.findById(session.getPlanId())
				.orElseThrow(() -> new IllegalArgumentException("考试计划不存在"));
		if (!"PUBLISHED".equals(session.getStatus())
				|| !"PUBLISHED".equals(plan.getStatus())
				|| !offering.getSemesterCode().equals(plan.getSemesterCode())
				|| mappings.findBySessionId(sessionId).stream()
						.map(ExamSessionOffering::getOfferingId)
						.noneMatch(offeringId::equals)) {
			throw new IllegalArgumentException("考试场次未发布或未关联该学期课程开设");
		}
	}

	/** Imports all roster members atomically; a missing projection never becomes a zero score. */
	@Transactional
	public int sync(Gradebook gradebook, List<AssessmentComponent> components,
			List<GradebookStudent> roster) {
		if (roster.isEmpty()) {
			throw new IllegalStateException("成绩册没有学生快照");
		}
		List<AssessmentComponent> examComponents = components.stream()
				.filter(component -> "EXAM".equals(component.getSourceType()))
				.toList();
		if (examComponents.isEmpty()) {
			throw new IllegalStateException("成绩册方案没有考试来源项目");
		}
		Map<String, GradeItem> existing = new HashMap<>();
		for (GradeItem item : items.findByGradebookId(gradebook.getId())) {
			existing.put(item.getComponentId() + ":" + item.getStudentId(), item);
		}
		int imported = 0;
		for (AssessmentComponent component : examComponents) {
			validateBinding(gradebook.getOfferingId(), component.getSourceRefId());
			ExamSession session = sessions.findById(component.getSourceRefId()).orElseThrow();
			if (!"PUBLISHED".equals(session.getScoreStatus())) {
				throw new IllegalStateException("考试逐题成绩尚未发布：" + session.getId());
			}
			List<DataGradeEventFact> sourceFacts = facts
					.findByEventTypeAndAggregateIdAndOfferingId(
							EXAM_EVENT, session.getId(), gradebook.getOfferingId());
			Map<String, DataGradeEventFact> byStudent = new HashMap<>();
			for (DataGradeEventFact fact : sourceFacts) {
				if (fact.getStudentId() == null
						|| byStudent.putIfAbsent(fact.getStudentId(), fact) != null) {
					throw new IllegalStateException("考试成绩来源存在重复或空考生");
				}
			}
			List<String> missing = new ArrayList<>();
			for (GradebookStudent student : roster) {
				if (!byStudent.containsKey(student.getStudentId())) {
					missing.add(student.getStudentNo());
				}
			}
			if (!missing.isEmpty()) {
				throw new IllegalStateException("考试成绩事件尚未覆盖全部学生："
						+ missing.stream().limit(5).collect(Collectors.joining(","))
						+ (missing.size() > 5 ? " 等" + missing.size() + "人" : ""));
			}
			for (GradebookStudent student : roster) {
				DataGradeEventFact fact = byStudent.get(student.getStudentId());
				if (fact.getScore() == null || fact.getMaxScore() == null
						|| fact.getMaxScore().signum() <= 0
						|| fact.getScore().signum() < 0
						|| fact.getScore().compareTo(fact.getMaxScore()) > 0) {
					throw new IllegalStateException("考试成绩事实分值无效：" + student.getStudentNo());
				}
				String key = component.getId() + ":" + student.getStudentId();
				GradeItem item = existing.get(key);
				if (item != null && item.getSourceEventId() != null
						&& !item.getSourceEventId().equals(fact.getEventId())) {
					throw new IllegalStateException("考试来源事件已变化，需走成绩更正流程");
				}
				if (item == null) {
					item = new GradeItem();
					item.setGradebookId(gradebook.getId());
					item.setComponentId(component.getId());
					item.setStudentId(student.getStudentId());
				} else if (item.getSourceEventId() == null && item.getRawScore() != null) {
					throw new IllegalStateException("考试项目已有人工分数，不能覆盖");
				}
				BigDecimal normalized = fact.getScore()
						.divide(fact.getMaxScore(), 8, RoundingMode.HALF_UP)
						.multiply(component.getMaxScore())
						.setScale(2, RoundingMode.HALF_UP);
				item.setRawScore(normalized);
				item.setConvertedScore(normalized);
				item.setSourceEventId(fact.getEventId());
				item.setSpecialStatus(null);
				item.setRemark("考试场次 " + session.getId() + "；原始得分 "
						+ fact.getScore() + "/" + fact.getMaxScore());
				items.save(item);
				imported++;
			}
		}
		return imported;
	}

	public record ExamSourceOption(String sessionId, LocalDate examDate,
			String startTime, String scoreStatus) {
	}
}
