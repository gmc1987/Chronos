package com.chronos.education.scheduling.model;

/** 发布或应用前的单条课表差异。 */
public record ScheduleDiffItem(
		String changeType,
		String offeringId,
		String courseName,
		String teachingClassName,
		String teacherName,
		String beforeSlot,
		String afterSlot) {
}
