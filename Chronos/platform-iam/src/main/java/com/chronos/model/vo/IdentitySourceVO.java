package com.chronos.model.vo;

import java.time.LocalDateTime;

public record IdentitySourceVO(
		String id,
		String sourceCode,
		String name,
		String sourceType,
		String issuerUrl,
		String clientId,
		String status,
		LocalDateTime lastTestAt) {
}
