package com.chronos.education.homeschool.dto;

import java.time.OffsetDateTime;

/** 家校通知发布事件，使用教育领域 outbox 投递，不复用工作流 outbox。 */
public final class HomeNoticeEventContracts {
	private HomeNoticeEventContracts() {
	}

	public record HomeNoticePublishedV1(
			String eventId,
			String eventType,
			OffsetDateTime occurredAt,
			int payloadVersion,
			String noticeId,
			String classId,
			int targetCount,
			String publisherUsername) {
	}
}
