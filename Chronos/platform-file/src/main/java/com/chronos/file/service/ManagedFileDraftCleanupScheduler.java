package com.chronos.file.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期回收没有绑定到流程实例的过期表单草稿附件。 */
@Component
@EnableScheduling
public class ManagedFileDraftCleanupScheduler {
	private final ManagedFileService files;
	private final long retentionHours;

	public ManagedFileDraftCleanupScheduler(
			ManagedFileService files,
			@Value("${chronos.file.draft-retention-hours:24}") long retentionHours) {
		this.files = files;
		this.retentionHours = Math.max(1, retentionHours);
	}

	@Scheduled(fixedDelayString = "${chronos.file.draft-cleanup-ms:3600000}")
	public void cleanup() {
		LocalDateTime cutoff = LocalDateTime.now()
				.minus(retentionHours, ChronoUnit.HOURS);
		files.cleanupExpiredDrafts(cutoff);
	}
}
