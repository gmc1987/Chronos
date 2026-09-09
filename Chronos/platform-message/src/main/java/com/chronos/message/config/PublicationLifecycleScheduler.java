package com.chronos.message.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chronos.message.service.iService.IPublicationService;

@Component
public class PublicationLifecycleScheduler {
	private final IPublicationService service;

	public PublicationLifecycleScheduler(IPublicationService service) {
		this.service = service;
	}

	/** 多节点重复扫描是安全的，状态迁移仅从 SCHEDULED/PUBLISHED 向前推进。 */
	@Scheduled(fixedDelayString = "${chronos.message.lifecycle-delay-ms:30000}")
	public void advance() {
		service.advanceLifecycle();
	}
}
