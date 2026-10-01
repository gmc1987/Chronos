package com.chronos.education.grade.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 成绩分析只暴露脱敏后的聚合结果，避免分析页面绕过成绩册权限读取原始成绩。
 */
public final class GradeAnalysisDtos {
	private GradeAnalysisDtos() {
	}

	public record FilterOption(String value, String label) {
	}

	public record AnalysisFilters(
			List<FilterOption> semesters,
			List<FilterOption> classes,
			List<FilterOption> grades,
			List<FilterOption> subjects) {
	}

	public record Summary(
			long studentCount,
			BigDecimal averageScore,
			BigDecimal highestScore,
			BigDecimal lowestScore,
			BigDecimal passRate,
			BigDecimal excellentRate) {
	}

	public record GroupMetric(
			String key,
			String name,
			String parentName,
			long studentCount,
			BigDecimal averageScore,
			BigDecimal passRate,
			BigDecimal excellentRate) {
	}

	public record Distribution(String range, long count) {
	}

	public record AnalysisResponse(
			String dimension,
			Summary summary,
			List<GroupMetric> groups,
			List<Distribution> distribution) {
	}

	public record TrendPoint(
			String semesterCode,
			BigDecimal averageScore,
			BigDecimal passRate,
			long studentCount) {
	}

	public record KnowledgeAnalysisResponse(
			boolean available,
			String reason,
			List<GroupMetric> groups) {
	}
}
