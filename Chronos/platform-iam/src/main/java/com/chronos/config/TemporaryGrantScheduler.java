package com.chronos.config;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.ITemporaryGrantRepository;
import com.chronos.Idao.IAdminUserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TemporaryGrantScheduler {
	private final ITemporaryGrantRepository grants;
	private final IAdminUserRepository users;

	@Scheduled(fixedDelayString = "${chronos.iam.temporary-grant-reaper-delay-ms:60000}")
	@Transactional
	public void expireGrants() {
		LocalDateTime now = LocalDateTime.now();
		grants.findByStatusAndValidUntilLessThanEqual("APPROVED", now).forEach(grant -> {
			grant.setStatus("EXPIRED");
			grants.save(grant);
		});
		grants.findByStatusAndValidFromLessThanEqualAndValidUntilGreaterThan("APPROVED", now, now)
				.forEach(grant -> {
					grant.setStatus("ACTIVE");
					grants.save(grant);
				});
		grants.findByStatusAndValidUntilLessThanEqual("ACTIVE", now).forEach(grant -> {
			grant.setStatus("EXPIRED");
			grants.save(grant);
			users.findById(grant.getUserId()).ifPresent(user -> {
				user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
				users.save(user);
			});
		});
	}
}
