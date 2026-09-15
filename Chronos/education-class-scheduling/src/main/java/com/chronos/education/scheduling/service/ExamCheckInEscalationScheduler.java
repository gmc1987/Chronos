package com.chronos.education.scheduling.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 定时升级考前未报到任务，通知通过既有 Outbox 可靠投递。 */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ExamCheckInEscalationScheduler {
	private final ExamCenterService exams;

	@Scheduled(fixedDelayString = "${education.exam.check-in-scan-ms:300000}")
	public void scan() {
		exams.escalateMissingCheckIns();
	}
}
