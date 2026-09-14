package com.chronos.education.scheduling.model;

import java.util.List;

/** 合班来源及成员同步结果；人数来自已选课成员，不由前端提交。 */
public record CombinedOfferingView(
		String offeringId,
		List<String> administrativeClassIds,
		int studentCount,
		int expectedStudentCount,
		boolean syncRequired) {
}
