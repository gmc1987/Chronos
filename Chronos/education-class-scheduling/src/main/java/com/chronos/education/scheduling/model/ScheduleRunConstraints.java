package com.chronos.education.scheduling.model;

import java.util.List;

/** Immutable, run-local solver rules; never persisted as school-wide constraints. */
public record ScheduleRunConstraints(List<TeacherSlot> teacherSlots) {
	public ScheduleRunConstraints {
		teacherSlots = teacherSlots == null ? List.of() : List.copyOf(teacherSlots);
	}

	public static ScheduleRunConstraints empty() {
		return new ScheduleRunConstraints(List.of());
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
