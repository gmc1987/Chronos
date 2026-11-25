package com.chronos.integration.service;

import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.SyncRun;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class IntegrationScheduler {
	private final SyncJobRepository jobs;
	private final SyncRunRepository runs;
	private final IntegrationExtensionService extension;

	@Scheduled(fixedDelayString = "${chronos.integration.scheduler-delay-ms:60000}")
	public void dispatch() {
		LocalDateTime now = LocalDateTime.now();
		for (var job : jobs.findByStatus("ENABLED")) {
			try {
				LocalDateTime last = runs.findFirstByJobIdOrderByStartedAtDesc(job.getId())
						.map(SyncRun::getStartedAt).orElse(job.getCreateTime());
				LocalDateTime next = CronExpression.parse(job.getCronExpression()).next(last);
				if (next != null && !next.isAfter(now)) extension.executeBatch(job.getId(), "integration-scheduler");
			} catch (IntegrationBoundaryException failure) {
				if (!"JOB_BUSY".equals(failure.getCode())) {
					log.warn("Integration sync dispatch failed: jobId={}, code={}", job.getId(), failure.getCode());
				}
			} catch (RuntimeException failure) {
				log.error("Integration sync dispatch failed: jobId={}", job.getId(), failure);
			}
		}
	}
}
