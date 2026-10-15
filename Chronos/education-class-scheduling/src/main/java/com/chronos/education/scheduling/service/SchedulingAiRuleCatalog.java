package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.SchedulingAiPlan;
import com.chronos.education.scheduling.model.SchedulingAiRuleClaim;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅登记求解器或权威日期业务已实现的规则；模型不能扩展目录语义。 */
public final class SchedulingAiRuleCatalog {
	public static final String VERSION = "SCHED_RULE_CATALOG_V1";
	private static final String VALIDATOR = "SCHEDULE_VALIDATOR_V1";

	private SchedulingAiRuleCatalog() {
	}

	public record Definition(String type, String granularity, String strength,
			String exceptionPolicy, String authority, String validatorVersion,
			String effectScope) {
	}

	public static Map<String, Definition> definitions() {
		Map<String, Definition> rules = new LinkedHashMap<>();
		rules.put("TEACHER_TIME", new Definition("TEACHER_TIME", "WEEK_SLOT", "HARD_OR_SOFT",
				"NONE", "TEACHER_PROFILE", VALIDATOR, "RUN_LOCAL"));
		rules.put("CONSECUTIVE_BLOCK", new Definition("CONSECUTIVE_BLOCK", "OFFERING_WEEK",
				"HARD", "NONE", "COURSE_OFFERING", VALIDATOR, "RUN_LOCAL"));
		rules.put("WEEK_PATTERN", new Definition("WEEK_PATTERN", "OFFERING_WEEK", "HARD",
				"NONE", "ACADEMIC_TERM", VALIDATOR, "RUN_LOCAL"));
		rules.put("LOCK_ENTRY", new Definition("LOCK_ENTRY", "WEEK_SLOT", "HARD",
				"NONE", "CURRENT_TIMETABLE", VALIDATOR, "RUN_LOCAL"));
		rules.put("SOFT_PRIORITY", new Definition("SOFT_PRIORITY", "TEACHER_WEEK", "SOFT",
				"BEST_EFFORT", "TEACHER_PROFILE", VALIDATOR, "RUN_LOCAL"));
		rules.put("SLOT_EXCLUSION", new Definition("SLOT_EXCLUSION", "WEEK_SLOT", "HARD",
				"NONE", "COURSE_OFFERING", VALIDATOR, "RUN_LOCAL"));
		for (String type : List.of("CALENDAR", "EXAM", "LEAVE")) {
			rules.put(type, new Definition(type, "DATE_SLOT", "HARD",
					"AUTHORITATIVE_ADJUSTMENT", switch (type) {
						case "CALENDAR" -> "SCHOOL_CALENDAR";
						case "EXAM" -> "PUBLISHED_EXAM_PLAN";
						default -> "APPROVED_TEACHER_LEAVE";
					}, VALIDATOR, "DATE_WORKFLOW"));
		}
		return Map.copyOf(rules);
	}

	public static List<SchedulingAiRuleClaim> freeze(SchedulingAiPlan plan) {
		List<SchedulingAiRuleClaim> claims = new ArrayList<>();
		plan.constraints().forEach(rule -> add(claims, "TEACHER_TIME", rule.sourceText(),
				"TEACHER", rule.teacherId(), rule.strength()));
		plan.offeringConstraints().forEach(rule -> add(claims, "CONSECUTIVE_BLOCK",
				rule.sourceText(), "OFFERING", rule.offeringId(), null));
		plan.weekRules().forEach(rule -> add(claims, "WEEK_PATTERN", rule.sourceText(),
				"OFFERING", rule.offeringId(), null));
		plan.lockedEntries().forEach(rule -> add(claims, "LOCK_ENTRY", rule.sourceText(),
				"ENTRY", rule.entryId(), null));
		plan.softPriorities().forEach(rule -> add(claims, "SOFT_PRIORITY", rule.sourceText(),
				"TEACHER", rule.teacherId(), null));
		plan.slotRules().forEach(rule -> add(claims, "SLOT_EXCLUSION", rule.sourceText(),
				rule.targetType(), rule.targetId(), null));
		plan.dateRules().forEach(rule -> add(claims, rule.type(), rule.sourceText(),
				"SEMESTER", plan.semesterCode(), null));
		return List.copyOf(claims);
	}

	private static void add(List<SchedulingAiRuleClaim> claims, String type, String source,
			String targetType, String targetId, String strength) {
		Definition definition = definitions().get(type);
		if (definition == null || source == null || source.isBlank()
				|| targetId == null || targetId.isBlank()) {
			throw new IllegalStateException("确认计划包含目录外或未解析的规则：" + type);
		}
		claims.add(new SchedulingAiRuleClaim(String.format("R%03d", claims.size() + 1), VERSION,
				type, source, targetType, targetId, definition.granularity(),
				strength == null ? definition.strength() : strength, definition.exceptionPolicy(),
				definition.authority(), definition.validatorVersion(), definition.effectScope()));
	}
}
