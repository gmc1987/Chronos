package com.chronos.education.scheduling.model;

/** 单条调课事故的批量重放结果。 */
public record CourseAdjustmentIncidentBatchItem(
		String id,
		String status,
		Integer retryCount,
		String message) {
}
