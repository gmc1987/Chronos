package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;

/** 版本列表只返回元数据，避免把可能很大的课表快照传给浏览器。 */
public record SchedulePlanVersionView(
		String id,
		String semesterCode,
		Integer versionNo,
		String status,
		Integer sourceVersionNo,
		Integer entryCount,
		String publishedBy,
		LocalDateTime publishedAt) {
	public static SchedulePlanVersionView from(SchedulePlanVersion value) {
		return new SchedulePlanVersionView(
				value.getId(),
				value.getSemesterCode(),
				value.getVersionNo(),
				value.getStatus(),
				value.getSourceVersionNo(),
				value.getEntryCount(),
				value.getPublishedBy(),
				value.getPublishedAt());
	}
}
