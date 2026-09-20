package com.chronos.education.scheduling.model;

import java.time.LocalDate;

/** 门户与管理端共用的教室申请展示视图，避免前端直接显示内部资源 ID。 */
public record ClassroomReservationView(
		String id,
		String businessKey,
		String applicantUsername,
		String semesterCode,
		String classroomId,
		String classroomName,
		LocalDate usageDate,
		Integer startPeriod,
		Integer durationPeriods,
		Integer attendeeCount,
		String purpose,
		String status,
		String failureMessage,
		String cancellationReason) {
}
