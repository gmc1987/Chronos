package com.chronos.education.homeschool.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;

public final class FamilyEngagementDtos {
	private FamilyEngagementDtos() {}
	public record FeedbackCommand(String studentId, @JsonAlias("subject") String title, String content,
			List<String> fileIds, LocalDateTime dueAt) {}
	public record FeedbackActionCommand(String status, String assignee, String comment) {}
	public record FeedbackResponse(String id, String parentId, String studentId, String classId,
			String title, String content, String status, String assignedTo, LocalDateTime dueAt,
			boolean overdue, LocalDateTime acceptedAt, LocalDateTime resolvedAt,
			LocalDateTime closedAt, LocalDateTime parentConfirmedAt, LocalDateTime reopenedAt) {}
	public record CommunicationCommand(String studentId, String channel, String subject,
			String content, String sensitiveContent, LocalDateTime occurredAt) {}
	public record CommunicationResponse(String id, String teacherUsername, String studentId,
			String classId, String channel, String subject, String content, String sensitiveContent,
			LocalDateTime occurredAt) {}
}
