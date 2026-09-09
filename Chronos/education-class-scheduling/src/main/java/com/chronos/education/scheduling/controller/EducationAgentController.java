package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.SchedulingAgentProposal;
import com.chronos.education.scheduling.service.EducationAgentService;
import com.chronos.education.scheduling.service.EducationAgentService.AcademicAnalysis;
import com.chronos.education.scheduling.service.EducationAgentService.ProposalCommand;

@RestController
@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use')")
public class EducationAgentController {
	private final EducationAgentService service;

	public EducationAgentController(EducationAgentService service) {
		this.service = service;
	}

	@GetMapping("/education/agents/scheduling/proposals")
	public ResultData<?> proposals(
			@RequestParam String semesterCode,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null
				? ok(service.proposals(semesterCode))
				: ok(service.proposals(semesterCode, page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/education/agents/scheduling/proposals")
	public ResultData<SchedulingAgentProposal> propose(
			@RequestBody ProposalCommand command,
			Authentication authentication) {
		return ok(service.propose(command, authentication));
	}

	@PostMapping("/education/agents/scheduling/proposals/{id}/confirm")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:confirm')")
	public ResultData<SchedulingAgentProposal> confirm(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.confirm(id, authentication));
	}

	@PostMapping("/education/agents/scheduling/proposals/{id}/reject")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:confirm')")
	public ResultData<SchedulingAgentProposal> reject(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.reject(id, authentication));
	}

	@GetMapping("/education/agents/academic/analyze")
	public ResultData<AcademicAnalysis> analyze(
			@RequestParam String semesterCode,
			Authentication authentication) {
		return ok(service.analyze(semesterCode, authentication));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
