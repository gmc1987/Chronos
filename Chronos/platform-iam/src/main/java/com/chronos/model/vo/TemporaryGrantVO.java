package com.chronos.model.vo;

import java.time.LocalDateTime;

public record TemporaryGrantVO(
		String id,
		String userId,
		String permissionCode,
		String reason,
		String requestedBy,
		String approvedBy,
		String secondApprovedBy,
		LocalDateTime validFrom,
		LocalDateTime validUntil,
		String status,
		LocalDateTime revokedAt) {
}
