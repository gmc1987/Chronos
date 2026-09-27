package com.chronos.model.vo;

import java.time.LocalDateTime;

public record MfaFactorVO(
		String id,
		String userId,
		String factorType,
		String status,
		LocalDateTime enrolledAt,
		LocalDateTime verifiedAt) {
}
