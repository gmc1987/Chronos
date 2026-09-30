package com.chronos.education.scheduling.model;

import java.util.List;

/** Immutable, run-local solver rules; never persisted as school-wide constraints. */
public record ScheduleRunConstraints(List<TeacherSlot> teacherSlots,
		List<OfferingDuration> offeringDurations,
		List<WeekRule> weekRules,
		List<LockedEntry> lockedEntries,
		List<SoftPriority> softPriorities) {
	public ScheduleRunConstraints {
		teacherSlots = teacherSlots == null ? List.of() : List.copyOf(teacherSlots);
		offeringDurations = offeringDurations == null ? List.of() : List.copyOf(offeringDurations);
		weekRules = weekRules == null ? List.of() : List.copyOf(weekRules);
		lockedEntries = lockedEntries == null ? List.of() : List.copyOf(lockedEntries);
		softPriorities = softPriorities == null ? List.of() : List.copyOf(softPriorities);
	}

	public ScheduleRunConstraints(List<TeacherSlot> teacherSlots) {
		this(teacherSlots, List.of(), List.of(), List.of(), List.of());
	}

	public ScheduleRunConstraints(List<TeacherSlot> teacherSlots, List<OfferingDuration> offeringDurations) {
		this(teacherSlots, offeringDurations, List.of(), List.of(), List.of());
	}

	public static ScheduleRunConstraints empty() {
		return new ScheduleRunConstraints(List.of(), List.of(), List.of(), List.of(), List.of());
	}

	public record WeekRule(String offeringId, String weekPattern, int startWeek, int endWeek) {
		public WeekRule {
			if (offeringId == null || offeringId.isBlank()
					|| !List.of("ALL", "ODD", "EVEN").contains(weekPattern)
					|| startWeek < 1 || endWeek > 52 || endWeek < startWeek) {
				throw new IllegalArgumentException("AI 教学周规则无效");
			}
		}
	}

	public record LockedEntry(String entryId, String offeringId, int dayOfWeek, int periodNo) {
		public LockedEntry {
			if (entryId == null || entryId.isBlank() || offeringId == null || offeringId.isBlank()
					|| dayOfWeek < 1 || dayOfWeek > 7 || periodNo < 1 || periodNo > 20) {
				throw new IllegalArgumentException("AI 临时锁课项无效");
			}
		}
	}

	public record SoftPriority(String kind, String teacherId) {
		public SoftPriority {
			if (!List.of("CAMPUS_SWITCH", "TEACHER_GAP", "SAME_DAY").contains(kind)
					|| teacherId == null || teacherId.isBlank()) {
				throw new IllegalArgumentException("AI 教师软偏好无效");
			}
		}
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
