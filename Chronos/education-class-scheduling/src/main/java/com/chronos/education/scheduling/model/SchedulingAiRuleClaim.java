package com.chronos.education.scheduling.model;

/** 人工确认时冻结的规则原话、目录语义与服务端验证器版本。 */
public record SchedulingAiRuleClaim(
		String claimId,
		String catalogVersion,
		String ruleType,
		String sourceText,
		String targetType,
		String targetId,
		String granularity,
		String strength,
		String exceptionPolicy,
		String authority,
		String validatorVersion,
		String effectScope) {
}
