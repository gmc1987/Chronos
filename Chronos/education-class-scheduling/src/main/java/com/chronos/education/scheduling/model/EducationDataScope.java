package com.chronos.education.scheduling.model;

import java.util.Set;

/** 教育领域将平台通用数据范围解析为可用于数据库过滤的资源集合。 */
public record EducationDataScope(
		boolean fullAccess,
		Set<String> campusIds,
		Set<String> gradeIds,
		Set<String> administrativeClassIds,
		Set<String> teacherIds) {
}
