package com.chronos.facility.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FacilityDispatchScheduler {
	private final FacilityMaintenanceService service;

	public FacilityDispatchScheduler(FacilityMaintenanceService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${chronos.facility.dispatch.scan-delay-ms:60000}")
	public void processDispatchTimeoutsAndRetries() {
		service.processDispatchGovernance(LocalDateTime.now(), 100);
	}

	@Scheduled(fixedDelayString = "${chronos.facility.dispatch.load-reconcile-ms:900000}")
	public void reconcileTechnicianLoads() {
		service.reconcileTechnicianLoads();
	}
}
