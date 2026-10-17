package com.chronos.education.scheduling.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs file generation outside the request transaction and persists terminal failures. */
@Component
public class EducationDataReportListener {
	private final EducationDataCenterService dataCenter;

	public EducationDataReportListener(EducationDataCenterService dataCenter) {
		this.dataCenter = dataCenter;
	}

	@Async("scheduleGenerationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void generate(EducationDataReportRequestedEvent event) {
		try {
			dataCenter.generateReport(event.taskId(), event.actor());
		} catch (Exception failure) {
			dataCenter.failReport(event.taskId(), failure.getMessage());
		}
	}
}
