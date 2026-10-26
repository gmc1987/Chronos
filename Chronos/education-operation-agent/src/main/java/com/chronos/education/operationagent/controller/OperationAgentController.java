package com.chronos.education.operationagent.controller;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.commons.model.ResultData;
import com.chronos.education.operationagent.model.OperationAgentEvent;
import com.chronos.education.operationagent.service.OperationAgentLlmService.DraftCommand;
import com.chronos.education.operationagent.service.OperationAgentLlmService.DraftResult;
import com.chronos.education.operationagent.service.OperationAgentManagedFileService;
import com.chronos.education.operationagent.service.OperationAgentService;
import com.chronos.education.operationagent.service.OperationAgentService.*;
import com.chronos.file.model.ManagedFileView;

@RestController
@RequestMapping("/agent/operations")
@PreAuthorize("isAuthenticated()")
public class OperationAgentController {
	private final OperationAgentService service;
	private final OperationAgentManagedFileService managedFiles;

	public OperationAgentController(
			OperationAgentService service,
			OperationAgentManagedFileService managedFiles) {
		this.service = service;
		this.managedFiles = managedFiles;
	}

	@PostMapping(value = "/files/admission-rosters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('education:operation-agent:manage')")
	public ResultData<ManagedFileView> uploadAdmissionRoster(
			@RequestPart("file") MultipartFile file,
			Authentication authentication) {
		return ok(managedFiles.uploadAdmissionRoster(file, authentication));
	}

	@GetMapping("/tools")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:view','education:operation-agent:manage','education:operation-agent:execute','education:operation-agent:review')")
	public ResultData<?> tools() {
		return ok(service.tools());
	}

	@GetMapping("/plans")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:view','education:operation-agent:manage','education:operation-agent:execute','education:operation-agent:review')")
	public ResultData<List<PlanView>> plans(Authentication authentication) {
		return ok(service.listPlans(authentication));
	}

	@PostMapping("/plans/draft")
	@PreAuthorize("hasAuthority('education:operation-agent:manage')")
	public ResultData<DraftResult> draft(
			@RequestBody DraftCommand command,
			Authentication authentication) {
		return ok(service.draftPlan(command, authentication));
	}

	@PostMapping("/plans")
	@PreAuthorize("hasAuthority('education:operation-agent:manage')")
	public ResultData<PlanView> create(
			@RequestBody PlanCommand command,
			Authentication authentication) {
		return ok(service.savePlan(null, command, authentication));
	}

	@PutMapping("/plans/{id}")
	@PreAuthorize("hasAuthority('education:operation-agent:manage')")
	public ResultData<PlanView> update(
			@PathVariable String id,
			@RequestBody PlanCommand command,
			Authentication authentication) {
		return ok(service.savePlan(id, command, authentication));
	}

	@DeleteMapping("/plans/{id}")
	@PreAuthorize("hasAuthority('education:operation-agent:manage')")
	public ResultData<Void> delete(
			@PathVariable String id,
			Authentication authentication) {
		service.deletePlan(id, authentication);
		return ok(null);
	}

	@PostMapping("/plans/{id}/runs")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> start(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.start(id, authentication));
	}

	@GetMapping("/runs")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:view','education:operation-agent:manage','education:operation-agent:execute','education:operation-agent:review')")
	public ResultData<List<RunView>> runs(Authentication authentication) {
		return ok(service.listRuns(authentication));
	}

	@GetMapping("/runs/{id}")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:view','education:operation-agent:manage','education:operation-agent:execute','education:operation-agent:review')")
	public ResultData<RunDetail> detail(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.detail(id, authentication));
	}

	@GetMapping("/runs/{id}/events")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:view','education:operation-agent:manage','education:operation-agent:execute','education:operation-agent:review')")
	public ResultData<List<OperationAgentEvent>> events(
			@PathVariable String id,
			@RequestParam(defaultValue = "0") long after,
			Authentication authentication) {
		return ok(service.events(id, after, authentication));
	}

	@PostMapping("/runs/{id}/pause")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> pause(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.requestPause(id, authentication));
	}

	@PostMapping("/runs/{id}/resume")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> resume(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.resume(id, authentication));
	}

	@PostMapping("/runs/{id}/approve")
	@PreAuthorize("hasAuthority('education:operation-agent:review')")
	public ResultData<RunView> approve(
			@PathVariable String id,
			@RequestBody(required = false) ReviewCommand command,
			Authentication authentication) {
		return ok(service.approve(id, command, authentication));
	}

	@PostMapping("/runs/{id}/approval-evidence/refresh")
	@PreAuthorize("hasAuthority('education:operation-agent:review')")
	public ResultData<RunDetail> refreshApprovalEvidence(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.refreshApprovalEvidence(id, authentication));
	}

	@PostMapping("/runs/{id}/reject")
	@PreAuthorize("hasAuthority('education:operation-agent:review')")
	public ResultData<RunView> reject(
			@PathVariable String id,
			@RequestBody ReviewCommand command,
			Authentication authentication) {
		return ok(service.reject(id, command, authentication));
	}

	@PostMapping("/runs/{id}/conditions")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> condition(
			@PathVariable String id,
			@RequestBody ConditionCommand command,
			Authentication authentication) {
		return ok(service.addCondition(id, command, authentication));
	}

	@PostMapping("/runs/{id}/inputs")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> input(
			@PathVariable String id,
			@RequestBody StepInputCommand command,
			Authentication authentication) {
		return ok(service.supplyStepInput(id, command, authentication));
	}

	@PostMapping("/runs/{id}/cancel")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> cancel(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.cancel(id, authentication));
	}

	@PostMapping("/runs/{id}/retry")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<RunView> retry(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.retry(id, authentication));
	}

	@PostMapping("/runs/{id}/promote")
	@PreAuthorize("hasAnyAuthority('education:operation-agent:execute','education:operation-agent:manage')")
	public ResultData<PromotionResult> promote(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.promoteCandidate(id, authentication));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
