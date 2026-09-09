package com.chronos.message.model;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端保存命令。受众与主体同事务提交，避免发布出无受众内容。 */
public record PublicationCommand(
		String publicationType,
		String contentType,
		String title,
		String summary,
		String content,
		String importance,
		Boolean pinned,
		Integer sortOrder,
		Boolean mustRead,
		String audienceMode,
		LocalDateTime readDeadline,
		Boolean approvalRequired,
		String approvalWorkflowDefinitionId,
		LocalDateTime publishAt,
		LocalDateTime expireAt,
		List<AudienceCommand> audiences) {
	public record AudienceCommand(
			String subjectType,
			String subjectId,
			Boolean includeChildren,
			Boolean excluded) {
	}
}
