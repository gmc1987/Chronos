package com.chronos.education.scheduling.service;

/** Starts report generation only after the task row has committed. */
public record EducationDataReportRequestedEvent(String taskId, String actor) {}
