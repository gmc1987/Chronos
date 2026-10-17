package com.chronos.knowledge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Restart-safe background dispatcher for vector-index mutations. */
@Component
public class KnowledgeIndexScheduler {
	private final KnowledgeIndexTaskProcessor processor;
	private final int batchSize;

	public KnowledgeIndexScheduler(
			KnowledgeIndexTaskProcessor processor,
			@Value("${chronos.knowledge.index.batch-size:20}") int batchSize) {
		this.processor = processor;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${chronos.knowledge.index.delay-ms:5000}")
	public void dispatch() {
		processor.dueIds(batchSize).forEach(processor::process);
	}
}
