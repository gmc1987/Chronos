package com.chronos.education.scheduling.service;

/** Governs optional links between exams and teaching schedules in a deployment. */
public interface ExamCourseIntegrationPolicy {
	boolean enabled();
}
