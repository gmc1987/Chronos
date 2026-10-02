package com.chronos.education.scheduling.model;

/** 日期规则只引用校历、考试与已批准请假事实，不在周课表中伪造日期约束。 */
public record SchedulingAiDateRule(String type, String sourceText) { }
