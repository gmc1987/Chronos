package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.SchedulingAiDateImpact;
import com.chronos.education.scheduling.model.SchedulingAiDateRule;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日期规则使用校历、考试计划和请假审批的权威数据；本服务不修改正式日期课表。 */
@Service
public class SchedulingAiDateImpactService {
	private final AcademicTermRepository terms;
	private final AcademicCalendarDayRepository calendarDays;
	private final ScheduleOccurrenceService occurrences;
	private final ExamCenterService exams;

	public SchedulingAiDateImpactService(AcademicTermRepository terms,
			AcademicCalendarDayRepository calendarDays,
			ScheduleOccurrenceService occurrences,
			ExamCenterService exams) {
		this.terms = terms;
		this.calendarDays = calendarDays;
		this.occurrences = occurrences;
		this.exams = exams;
	}

	@Transactional(readOnly = true)
	public SchedulingAiDateImpact impact(SchedulingAiPlan plan) {
		Set<String> types = plan.dateRules().stream()
				.map(SchedulingAiDateRule::type).collect(Collectors.toSet());
		String semester = plan.semesterCode();
		var calendar = List.<com.chronos.education.scheduling.model.AcademicCalendarDay>of();
		var makeups = List.<com.chronos.education.scheduling.model.PendingMakeupView>of();
		var substitutions = List.<com.chronos.education.scheduling.model.TeacherLeaveCoverageView>of();
		List<SchedulingAiDateImpact.ExamPlanImpact> examPlans = new ArrayList<>();
		if (types.contains("CALENDAR")) {
			var term = terms.findByTermCode(semester)
					.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
			calendar = calendarDays.findByAcademicTermIdOrderByCalendarDate(term.getId());
			makeups = occurrences.pendingMakeups(semester);
		}
		if (types.contains("LEAVE")) {
			substitutions = occurrences.pendingTeacherLeaveCoverage(semester);
		}
		if (types.contains("EXAM")) {
			for (var exam : exams.plans(semester)) {
				if ("CANCELLED".equals(exam.getStatus())) continue;
				try {
					examPlans.add(new SchedulingAiDateImpact.ExamPlanImpact(
							exam.getId(), exam.getPlanName(), exam.getStatus(),
							exams.suspensionImpacts(exam.getId(), "AFFECTED_ONLY").size(), null));
				} catch (IllegalStateException exception) {
					examPlans.add(new SchedulingAiDateImpact.ExamPlanImpact(
							exam.getId(), exam.getPlanName(), exam.getStatus(), 0,
							exception.getMessage()));
				}
			}
		}
		return new SchedulingAiDateImpact(plan.dateRules(), calendar, makeups,
				substitutions, List.copyOf(examPlans));
	}
}
