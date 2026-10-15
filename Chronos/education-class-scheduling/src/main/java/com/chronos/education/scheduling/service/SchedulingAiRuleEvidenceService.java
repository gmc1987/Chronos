package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleCandidatePlanRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiDateImpact;
import com.chronos.education.scheduling.model.SchedulingAiRuleClaim;
import com.chronos.education.scheduling.model.SchedulingAiRuleEvidence;
import com.chronos.education.scheduling.model.SchedulingAiRuleEvidence.Row;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只读取求解器写入的候选快照；不能由模型声明规则已满足。 */
@Service
public class SchedulingAiRuleEvidenceService {
	private final ScheduleCandidatePlanRepository candidates;
	private final CourseOfferingRepository offerings;
	private final ObjectMapper json;
	private final SchedulingAiDateImpactService dateImpacts;
	private final SchedulePlanVersionService versions;

	public SchedulingAiRuleEvidenceService(ScheduleCandidatePlanRepository candidates,
			CourseOfferingRepository offerings, ObjectMapper json,
			SchedulingAiDateImpactService dateImpacts,
			SchedulePlanVersionService versions) {
		this.candidates = candidates;
		this.offerings = offerings;
		this.json = json;
		this.dateImpacts = dateImpacts;
		this.versions = versions;
	}

	@Transactional(readOnly = true)
	public SchedulingAiRuleEvidence evaluate(SchedulingAiPlan plan, ScheduleCandidateView candidate) {
		if (!SchedulingAiRuleCatalog.VERSION.equals(plan.ruleCatalogVersion())) {
			throw new IllegalStateException("该 AI Run 未冻结版本化规则，请新建 Run 后重新确认");
		}
		var snapshot = candidates.findById(candidate.id())
				.orElseThrow(() -> new IllegalArgumentException("候选方案不存在"));
		if (!plan.semesterCode().equals(snapshot.getSemesterCode())) {
			throw new IllegalStateException("候选与规则计划的学期不一致");
		}
		List<ScheduleEntry> entries = readEntries(snapshot.getSnapshotJson());
		String publicationBlocker = null;
		try {
			versions.assertExamAvailability(entries);
		} catch (IllegalStateException exception) {
			publicationBlocker = exception.getMessage();
		}
		Set<String> offeringIds = entries.stream().map(ScheduleEntry::getOfferingId)
				.collect(Collectors.toSet());
		Map<String, CourseOffering> byOffering = new HashMap<>();
		offerings.findAllById(offeringIds).forEach(item -> byOffering.put(item.getId(), item));
		SchedulingAiDateImpact dateImpact = plan.dateRules().isEmpty()
				? null : dateImpacts.impact(plan);
		List<Row> evidence = new ArrayList<>();
		for (SchedulingAiRuleClaim claim : plan.ruleClaims()) {
			if (!SchedulingAiRuleCatalog.VERSION.equals(claim.catalogVersion())) {
				throw new IllegalStateException("规则目录版本不一致");
			}
			evidence.add(evaluateOne(plan, candidate, claim, entries, byOffering,
					dateImpact, publicationBlocker));
		}
		return new SchedulingAiRuleEvidence(candidate.id(), plan.ruleCatalogVersion(),
				candidate.unscheduledLessons() == null ? 0 : candidate.unscheduledLessons(),
				publicationBlocker,
				List.copyOf(evidence));
	}

	private Row evaluateOne(SchedulingAiPlan plan, ScheduleCandidateView candidate,
			SchedulingAiRuleClaim claim, List<ScheduleEntry> entries,
			Map<String, CourseOffering> byOffering, SchedulingAiDateImpact dateImpact,
			String publicationBlocker) {
		if ("DATE_WORKFLOW".equals(claim.effectScope())) {
			int pending = switch (claim.ruleType()) {
				case "CALENDAR" -> dateImpact.pendingMakeups().size();
				case "LEAVE" -> dateImpact.pendingSubstitutions().size();
				case "EXAM" -> dateImpact.examPlans().stream()
						.filter(item -> "PUBLISHED".equals(item.status()))
						.mapToInt(item -> item.affectedLessonCount()).sum();
				default -> throw new IllegalStateException("没有日期规则核验器：" + claim.ruleType());
			};
			int draftExamImpact = "EXAM".equals(claim.ruleType())
				? dateImpact.examPlans().stream().filter(item -> !"PUBLISHED".equals(item.status()))
						.mapToInt(item -> item.affectedLessonCount()).sum() : 0;
			boolean examConflict = "EXAM".equals(claim.ruleType())
					&& publicationBlocker != null;
			return new Row(claim, examConflict ? "VIOLATED" : "SEPARATE_WORKFLOW",
					pending, examConflict ? 1 : 0,
					"当前权威业务数据中的待处理课次 " + pending
							+ (draftExamImpact > 0 ? "；未发布考试草稿另有 " + draftExamImpact + " 节待审批" : "")
							+ (examConflict ? "；候选与已发布考试冲突：" + publicationBlocker : "")
							+ "；须在独立日期流程核对，周课表候选不能结清");
		}
		int observed = 0;
		int violations = 0;
		switch (claim.ruleType()) {
			case "TEACHER_TIME" -> {
				var rule = plan.constraints().stream().filter(item ->
						item.teacherId().equals(claim.targetId())
								&& item.sourceText().equals(claim.sourceText())).findFirst().orElseThrow();
				for (ScheduleEntry entry : entries) {
					CourseOffering offering = byOffering.get(entry.getOfferingId());
					if (offering != null && claim.targetId().equals(offering.getTeacherId())
							&& entry.getDayOfWeek().equals(rule.dayOfWeek())
							&& covers(entry, rule.periodNo())) observed++;
				}
				if ("HARD".equals(claim.strength())) violations = observed;
				return result(claim, candidate, observed, violations,
						"HARD".equals(claim.strength()) ? "禁排命中课时" : "偏好时段命中课时");
			}
			case "CONSECUTIVE_BLOCK" -> {
				var rule = plan.offeringConstraints().stream().filter(item ->
						item.offeringId().equals(claim.targetId())
								&& item.sourceText().equals(claim.sourceText())).findFirst().orElseThrow();
				for (ScheduleEntry entry : entries) {
					if (!claim.targetId().equals(entry.getOfferingId())) continue;
					observed++;
					if (!Integer.valueOf(rule.periods()).equals(entry.getDurationPeriods())) violations++;
				}
			}
			case "WEEK_PATTERN" -> {
				var rule = plan.weekRules().stream().filter(item ->
						item.offeringId().equals(claim.targetId())
								&& item.sourceText().equals(claim.sourceText())).findFirst().orElseThrow();
				for (ScheduleEntry entry : entries) {
					if (!claim.targetId().equals(entry.getOfferingId())) continue;
					observed++;
					if (!rule.weekPattern().equals(entry.getWeekPattern())
							|| entry.getStartWeek() < rule.startWeek()
							|| entry.getEndWeek() > rule.endWeek()) violations++;
				}
			}
			case "LOCK_ENTRY" -> {
				var rule = plan.lockedEntries().stream().filter(item ->
						item.entryId().equals(claim.targetId())).findFirst().orElseThrow();
				observed = (int) entries.stream().filter(entry ->
						rule.offeringId().equals(entry.getOfferingId())
								&& entry.getDayOfWeek() == rule.dayOfWeek()
								&& covers(entry, rule.periodNo())).count();
				violations = observed == 0 ? 1 : 0;
			}
			case "SLOT_EXCLUSION" -> {
				var rule = plan.slotRules().stream().filter(item ->
						item.targetType().equals(claim.targetType())
								&& item.targetId().equals(claim.targetId())
								&& item.sourceText().equals(claim.sourceText())).findFirst().orElseThrow();
				for (ScheduleEntry entry : entries) {
					CourseOffering offering = byOffering.get(entry.getOfferingId());
					if (offering == null || entry.getDayOfWeek() != rule.dayOfWeek()
							|| !covers(entry, rule.periodNo())) continue;
					if ("OFFERING".equals(rule.targetType())
							&& rule.targetId().equals(entry.getOfferingId())
							|| !"OFFERING".equals(rule.targetType())
							&& rule.targetId().equals(offering.getTeacherId())) observed++;
				}
				violations = observed;
			}
			case "SOFT_PRIORITY" -> {
				return new Row(claim, "OBSERVED", 0, 0,
						"软偏好没有逐教师的固定达标阈值；候选总指标可比较，但不能宣称本规则已满足");
			}
			default -> throw new IllegalStateException("没有规则核验器：" + claim.ruleType());
		}
		return result(claim, candidate, observed, violations, "候选快照逐条核验");
	}

	private Row result(SchedulingAiRuleClaim claim, ScheduleCandidateView candidate,
			int observed, int violations, String detail) {
		String status = violations > 0 ? "VIOLATED"
				: candidate.unscheduledLessons() != null && candidate.unscheduledLessons() > 0
						? "PENDING_UNSCHEDULED"
						: observed == 0 && Set.of("CONSECUTIVE_BLOCK", "WEEK_PATTERN").contains(claim.ruleType())
								? "PENDING_NO_ENTRY" : "SATISFIED";
		return new Row(claim, status, observed, violations, detail);
	}

	private boolean covers(ScheduleEntry entry, int period) {
		return entry.getPeriodNo() <= period
				&& period < entry.getPeriodNo() + Math.max(1, entry.getDurationPeriods());
	}

	private List<ScheduleEntry> readEntries(String value) {
		try {
			return json.readValue(value, new TypeReference<>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案快照损坏", exception);
		}
	}
}
