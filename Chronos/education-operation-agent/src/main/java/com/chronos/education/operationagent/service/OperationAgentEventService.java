package com.chronos.education.operationagent.service;

import org.springframework.stereotype.Service;
import com.chronos.education.operationagent.dao.OperationAgentEventRepository;
import com.chronos.education.operationagent.model.OperationAgentEvent;

@Service
public class OperationAgentEventService {
	private final OperationAgentEventRepository events;

	public OperationAgentEventService(OperationAgentEventRepository events) {
		this.events = events;
	}

	public synchronized OperationAgentEvent append(
			String runId,
			String type,
			String level,
			String title,
			String detail,
			String metadataJson) {
		OperationAgentEvent event = new OperationAgentEvent();
		event.setRunId(runId);
		event.setEventSequence(events.countByRunId(runId) + 1);
		event.setEventType(type);
		event.setLevel(level);
		event.setTitle(title);
		event.setDetail(detail);
		event.setMetadataJson(metadataJson);
		return events.save(event);
	}
}
