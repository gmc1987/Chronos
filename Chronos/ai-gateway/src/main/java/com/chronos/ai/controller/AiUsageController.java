package com.chronos.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.ai.model.AiUsagePolicy;
import com.chronos.ai.service.AiUsageGovernanceService;
import com.chronos.commons.model.ResultData;

/** Administrative endpoints for AI rate limits, budgets, and audited usage. */
@RestController
public class AiUsageController {
	private final AiUsageGovernanceService service;

	public AiUsageController(AiUsageGovernanceService service) {
		this.service = service;
	}

	@GetMapping("/ai-model/usage/summary")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:view','ai:model:manage')")
	public ResultData<?> summary() {
		return ok(service.summary());
	}

	@GetMapping("/ai-model/usage/records")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:view','ai:model:manage')")
	public ResultData<?> records(
			@RequestParam(required = false) String modelId,
			@RequestParam(required = false) String username,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ok(service.records(modelId, username, page, size));
	}

	@GetMapping("/ai-model/usage/policies")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:view','ai:model:manage')")
	public ResultData<?> policies() {
		return ok(service.policies());
	}

	@PostMapping("/ai-model/usage/policies")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:manage')")
	public ResultData<?> createPolicy(@RequestBody AiUsagePolicy command) {
		return ok(service.savePolicy(null, command));
	}

	@PutMapping("/ai-model/usage/policies/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:manage')")
	public ResultData<?> updatePolicy(@PathVariable String id, @RequestBody AiUsagePolicy command) {
		return ok(service.savePolicy(id, command));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
