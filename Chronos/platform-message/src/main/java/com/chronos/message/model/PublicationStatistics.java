package com.chronos.message.model;

import java.time.LocalDateTime;
import java.util.List;

public record PublicationStatistics(
		String publicationId,
		long recipientCount,
		long readCount,
		long unreadCount,
		double readRate,
		LocalDateTime readDeadline,
		boolean deadlinePassed,
		List<String> unreadUsers) {
}
