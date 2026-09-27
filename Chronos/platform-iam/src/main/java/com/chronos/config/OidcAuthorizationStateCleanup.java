package com.chronos.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chronos.Idao.IOidcAuthorizationStateRepository;

import lombok.RequiredArgsConstructor;

/** Prevents abandoned browser authorization attempts from accumulating indefinitely. */
@Component
@RequiredArgsConstructor
public class OidcAuthorizationStateCleanup {
	private final IOidcAuthorizationStateRepository states;

	@Scheduled(fixedDelayString = "${chronos.iam.oidc-state-cleanup-delay-ms:600000}")
	public void deleteExpiredStates() {
		states.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
	}
}
