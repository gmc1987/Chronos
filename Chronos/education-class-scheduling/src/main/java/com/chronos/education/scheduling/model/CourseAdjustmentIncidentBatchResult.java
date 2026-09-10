package com.chronos.education.scheduling.model;

import java.util.List;

/** 批量重放采用部分成功语义，调用方可以明确看到每一条的结果。 */
public record CourseAdjustmentIncidentBatchResult(
		int requested,
		int succeeded,
		int failed,
		List<CourseAdjustmentIncidentBatchItem> items) {
}
