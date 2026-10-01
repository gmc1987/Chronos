package com.chronos.education.meeting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 将已结束的已发布会议收口为已完成，同时保留完整历史记录。 */
@Component
@RequiredArgsConstructor
public class MeetingLifecycleScheduler {
	private final MeetingCenterService meetings;

	@Scheduled(fixedDelayString = "${education.meeting.completion-scan-ms:300000}")
	public void completeExpiredMeetings() {
		meetings.completeExpiredMeetings();
	}
}
