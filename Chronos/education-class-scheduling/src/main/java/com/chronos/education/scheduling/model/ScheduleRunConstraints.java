package com.chronos.education.scheduling.model;

import java.util.List;

/** Immutable, run-local solver rules; never persisted as school-wide constraints. */
public record ScheduleRunConstraints(List<TeacherSlot> teacherSlots,
		List<OfferingDuration> offeringDurations) {
	public ScheduleRunConstraints {
		teacherSlots = teacherSlots == null ? List.of() : List.copyOf(teacherSlots);
		offeringDurations = offeringDurations == null ? List.of() : List.copyOf(offeringDurations);
	}

	public ScheduleRunConstraints(List<TeacherSlot> teacherSlots) {
		this(teacherSlots, List.of());
	}

	public static ScheduleRunConstraints empty() {
		return new ScheduleRunConstraints(List.of(), List.of());
	}

	public record OfferingDuration(String offeringId, int periods) {
		public OfferingDuration {
			if (offeringId == null || offeringId.isBlank() || periods != 2) {
				throw new IllegalArgumentException("AI 连堂偏好必须指定教学任务且为两节");
			}
		}
	}
	public record TeacherSlot(
			String teacherId,
			int dayOfWeek,
			int periodNo,
			String type) {
		public TeacherSlot {
			if (teacherId == null || teacherId.isBlank()
					|| dayOfWeek < 1 || dayOfWeek > 7
					|| periodNo < 1 || periodNo > 20
					|| !List.of("FORBIDDEN", "PREFERRED").contains(type)) {
				throw new IllegalArgumentException("AI 动态教师时间规则无效");
			}
		}
	}
}
