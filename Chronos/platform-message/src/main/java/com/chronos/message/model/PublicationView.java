package com.chronos.message.model;

import java.time.LocalDateTime;
import java.util.List;

import com.chronos.message.model.PublicationCommand.AudienceCommand;

public record PublicationView(
		String id,
		String publicationType,
		String contentType,
		String title,
		String summary,
		String content,
		String status,
		String importance,
		Boolean pinned,
		Integer sortOrder,
		Boolean mustRead,
		String audienceMode,
		LocalDateTime readDeadline,
		Boolean approvalRequired,
		String approvalStatus,
		String approvalWorkflowDefinitionId,
		String approvalInstanceId,
		String reviewComment,
		LocalDateTime publishAt,
		LocalDateTime expireAt,
		LocalDateTime publishedAt,
		Boolean archived,
		LocalDateTime archivedAt,
		String archivedBy,
		Integer versionNo,
		String createBy,
		LocalDateTime createTime,
		boolean read,
		long readCount,
		long recipientCount,
		List<AudienceCommand> audiences,
		List<AttachmentView> attachments) {
	public record AttachmentView(
			String id,
			String originalName,
			String contentType,
			Long fileSize,
			String sha256,
			Boolean primaryContent) {
	}
}
