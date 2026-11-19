package com.chronos.education.operationagent.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chronos.education.operationagent.model.OperationAgentRun;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
class OperationAgentReviewScope {
	private final ObjectMapper mapper;

	OperationAgentReviewScope(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	boolean allows(OperationAgentRun run, Set<String> visibleOrganizations) {
		if (visibleOrganizations == null) {
			return true;
		}
		if (visibleOrganizations.isEmpty() || run.getPlanSnapshotJson() == null) {
			return false;
		}
		// 运行的机构取自冻结的工具参数。旧运行没有可核对的机构时，仅全局审核员可复核。
		Set<String> organizations = new HashSet<>();
		try {
			for (var step : mapper.readTree(run.getPlanSnapshotJson()).path("stepConfigs")) {
				String organization = step.path("organizationId").asText("").trim();
				if (!organization.isEmpty()) {
					organizations.add(organization);
				}
			}
		} catch (JsonProcessingException exception) {
			return false;
		}
		return !organizations.isEmpty() && visibleOrganizations.containsAll(organizations);
	}
}
