package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.AgentRun;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Restricts comparison and preview to successfully generated candidates of this Run. */
@Service
@Transactional(readOnly = true)
public class SchedulingAgentCandidateService {
	private final ScheduleGenerationJobService jobs;
	private final AutoSchedulingService scheduling;
	private final EducationDataScopeService scopes;
	private final ObjectMapper json;

	public SchedulingAgentCandidateService(ScheduleGenerationJobService jobs,
			AutoSchedulingService scheduling, EducationDataScopeService scopes, ObjectMapper json) {
		this.jobs = jobs;
		this.scheduling = scheduling;
		this.scopes = scopes;
		this.json = json;
	}

	public List<ScheduleCandidateView> compare(AgentRun run, String actor, List<String> candidateIds) {
		if (candidateIds == null || candidateIds.size() < 2 || candidateIds.size() > 5
				|| candidateIds.stream().anyMatch(id -> id == null || id.isBlank())
				|| candidateIds.size() != Set.copyOf(candidateIds).size()) {
			throw new IllegalArgumentException("请选择本轮 2 到 5 个不同的候选方案");
		}
		authorize(run, actor, candidateIds);
		return scheduling.compare(candidateIds);
	}

	public ScheduleDiffView preview(AgentRun run, String actor, String candidateId) {
		if (candidateId == null || candidateId.isBlank()) {
			throw new IllegalArgumentException("候选 ID 不能为空");
		}
		authorize(run, actor, List.of(candidateId));
		return scheduling.preview(candidateId);
	}

	private void authorize(AgentRun run, String actor, List<String> candidateIds) {
		scopes.assertFullAccess(scopes.resolve(actor));
		if (!actor.equals(run.getOwnerUsername()) || run.getRelatedJobId() == null) {
			throw new org.springframework.security.access.AccessDeniedException("无权查看本轮候选");
		}
		ScheduleGenerationJob job = jobs.require(run.getRelatedJobId());
		if (!"SUCCEEDED".equals(job.getStatus()) || !run.getId().equals(job.getAgentRunId())
				|| !actor.equals(job.getRequestedBy())
				|| !run.getSemesterCode().equals(job.getSemesterCode())
				|| job.getResultCandidateIds() == null) {
			throw new IllegalStateException("本轮排课尚无可用候选");
		}
		try {
			Set<String> permitted = Set.copyOf(json.readValue(job.getResultCandidateIds(),
					new TypeReference<List<String>>() { }));
			if (!permitted.containsAll(candidateIds)) {
				throw new org.springframework.security.access.AccessDeniedException("候选不属于当前 Run");
			}
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("排课任务候选数据损坏", exception);
		}
	}
}
