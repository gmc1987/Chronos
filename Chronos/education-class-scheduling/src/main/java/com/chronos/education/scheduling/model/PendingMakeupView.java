package com.chronos.education.scheduling.model;

import java.time.LocalDate;

/** 非教学日原本应上的课程，尚未通过调休课表或日期补课安置。 */
public record PendingMakeupView(LocalDate sourceDate, ScheduleEntryView entry) { }
