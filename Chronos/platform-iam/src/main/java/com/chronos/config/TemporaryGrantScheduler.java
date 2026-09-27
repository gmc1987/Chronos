package com.chronos.config;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.ITemporaryGrantRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TemporaryGrantScheduler {
	private final ITemporaryGrantRepository grants;

	@Scheduled(fixedDelayString = "${chronos.iam.temporary-grant-reaper-delay-ms:60000}")
	@Transactional
	public void expireGrants() {
		LocalDateTime now = LocalDateTime.now();
		grants.findByStatusAndValidUntilBefore("APPROVED", now).forEach(grant -> grant.setStatus("EXPIRED"));
		grants.findByStatusAndValidUntilBefore("ACTIVE", now).forEach(grant -> grant.setStatus("EXPIRED"));
	}
}
