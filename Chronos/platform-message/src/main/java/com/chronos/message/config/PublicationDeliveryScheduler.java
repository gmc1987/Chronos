package com.chronos.message.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chronos.message.service.impl.PublicationDeliveryService;

@Component
public class PublicationDeliveryScheduler {
	private final PublicationDeliveryService service;

	public PublicationDeliveryScheduler(PublicationDeliveryService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${chronos.message.delivery-delay-ms:10000}")
	public void dispatch() {
		service.dispatchBatch();
	}
}
