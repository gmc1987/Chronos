package com.chronos.education.meeting.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.chronos.education.meeting.model.Meeting;
import com.chronos.workflow.WorkflowNotificationService;

import lombok.RequiredArgsConstructor;

/** 会议消息复用平台可靠 Outbox，业务事务回滚时不会留下幽灵邀请。 */
@Service
@RequiredArgsConstructor
public class MeetingNotificationService {
	private final WorkflowNotificationService notifications;

	public void invite(Meeting meeting, String username, String occurrence) {
		notifications.enqueueUserEvent(
				"EDUCATION_MEETING_INVITATION",
				meeting.getId(),
				username,
				"会议邀请：" + meeting.getTitle(),
				meeting.getStartTime() + " 至 " + meeting.getEndTime(),
				occurrence);
	}

	public void roomApproval(Meeting meeting, String approver) {
		notifications.enqueueUserEvent(
				"EDUCATION_MEETING_ROOM_APPROVAL",
				meeting.getId(),
				approver,
				"会议室预约待审批",
				meeting.getTitle() + "，" + meeting.getStartTime(),
				"ROOM_APPROVAL_REQUESTED:" + fingerprint(meeting));
	}

	public void organizerResult(Meeting meeting, String result) {
		notifications.enqueueUserEvent(
				"EDUCATION_MEETING_ROOM_DECIDED",
				meeting.getId(),
				meeting.getOrganizerUsername(),
				"会议室预约结果",
				meeting.getTitle() + "：" + result,
				"ROOM_DECIDED:" + fingerprint(meeting));
	}

	public void changed(Meeting meeting, String username) {
		// 通知去重键取自会议实际内容，不依赖 JPA 在事务提交时才递增的版本号。
		String changeFingerprint = Integer.toUnsignedString(Objects.hash(
				meeting.getTitle(), meeting.getStartTime(), meeting.getEndTime(),
				meeting.getRoomId(), meeting.getJoinUrl()));
		notifications.enqueueUserEvent(
				"EDUCATION_MEETING_CHANGED",
				meeting.getId(),
				username,
				"会议安排已变更：" + meeting.getTitle(),
				"请重新确认时间、地点或线上会议链接。",
				"CHANGED:" + changeFingerprint);
	}

	public void cancelled(Meeting meeting, String username) {
		notifications.enqueueUserEvent(
				"EDUCATION_MEETING_CANCELLED",
				meeting.getId(),
				username,
				"会议已取消：" + meeting.getTitle(),
				meeting.getCancelReason(),
				"CANCELLED");
	}

	public void removed(Meeting meeting, String username) {
		notifications.enqueueUserEvent(
				"EDUCATION_MEETING_REMOVED",
				meeting.getId(),
				username,
				"您已不在会议参会名单中",
				meeting.getTitle() + "，" + meeting.getStartTime(),
				"REMOVED:" + fingerprint(meeting));
	}

	private String fingerprint(Meeting meeting) {
		return Integer.toUnsignedString(Objects.hash(
				meeting.getTitle(), meeting.getStartTime(), meeting.getEndTime(),
				meeting.getRoomId(), meeting.getJoinUrl(), meeting.getStatus()));
	}
}
