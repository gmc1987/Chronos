package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Rebuilds solver inputs only from the server-confirmed plan, never a tool caller's payload. */
@Service
public class SchedulingAgentPlanValidator {
	private final EducationDataScopeService scopes;
	private final ObjectMapper json;
	private final SchedulingAgentTimetableService timetable;

	public SchedulingAgentPlanValidator(EducationDataScopeService scopes, ObjectMapper json,
			SchedulingAgentTimetableService timetable) {
		this.scopes = scopes;
		this.json = json;
		this.timetable = timetable;
	}

	public Parameters fromConfirmedRun(AgentRun run, String actor) {
		if (run.getConfirmedPlanJson() == null || run.getConfirmedPlanJson().isBlank()) {
			throw new IllegalStateException("AI Run 尚未确认");
		}
		try {
			SchedulingAiPlan plan = json.readValue(run.getConfirmedPlanJson(), SchedulingAiPlan.class);
			if (!run.getSemesterCode().equals(plan.semesterCode())) {
				throw new IllegalStateException("AI Run 的学期与确认计划不一致");
			}
			return parameters(plan, actor);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI Run 确认计划损坏", exception);
		}
	}

	public SchedulingAiPlan fromDraftRun(AgentRun run) {
		if (run.getParsedPlanJson() == null || run.getParsedPlanJson().isBlank()) {
			throw new IllegalStateException("AI Run 缺少待确认计划");
		}
		try {
			SchedulingAiPlan plan = json.readValue(run.getParsedPlanJson(), SchedulingAiPlan.class);
			if (!run.getSemesterCode().equals(plan.semesterCode())) {
				throw new IllegalStateException("AI Run 的学期与待确认计划不一致");
			}
			return plan;
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI Run 待确认计划损坏", exception);
		}
	}

	public Parameters parameters(SchedulingAiPlan plan, String actor) {
		if (!plan.readyForConfirmation() || plan.candidateCount() < 1 || plan.candidateCount() > 5
				|| plan.mode() == null || !Set.of("GLOBAL", "LOCAL").contains(plan.mode())
				|| "LOCAL".equals(plan.mode()) && plan.selectedOfferingIds().isEmpty()) {
			throw new IllegalStateException("结构化需求不能执行排课");
		}
		var scope = scopes.resolve(actor);
		scopes.assertFullAccess(scope);
		var dimensions = timetable.dimensions(plan.semesterCode(), plan.mode(),
				plan.selectedOfferingIds());
		List<ScheduleRunConstraints.TeacherSlot> rules = plan.constraints().stream()
				.map(item -> {
					if (!"TEACHER_TIME".equals(item.kind())
							|| !"RESOLVED".equals(item.resolution())
							|| item.strength() == null
							|| !Set.of("HARD", "SOFT").contains(item.strength())
							|| item.teacherId() == null
							|| item.dayOfWeek() == null || item.periodNo() == null
							|| item.dayOfWeek() < 1 || item.periodNo() < 1
							|| item.dayOfWeek() > dimensions.weekdays()
							|| item.periodNo() > dimensions.periodsPerDay()) {
						throw new IllegalStateException("结构化规则未被求解器支持");
					}
					scopes.assertTeacherAccess(scope, item.teacherId());
					return new ScheduleRunConstraints.TeacherSlot(
							item.teacherId(), item.dayOfWeek(), item.periodNo(),
							"HARD".equals(item.strength()) ? "FORBIDDEN" : "PREFERRED");
				})
				.toList();
		AutoScheduleCommand command = new AutoScheduleCommand(
				plan.semesterCode(), "AI-" + plan.semesterCode(),
				"LOCAL".equals(plan.mode()) ? "LOCAL" : "FULL",
				plan.selectedOfferingIds(), plan.candidateCount(),
				dimensions.weekdays(), dimensions.periodsPerDay(), 1, dimensions.endWeek());
		return new Parameters(command, new ScheduleRunConstraints(rules));
	}

	public record Parameters(AutoScheduleCommand command, ScheduleRunConstraints constraints) {
	}
}
