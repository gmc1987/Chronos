package com.chronos.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.ai.model.AiModel;
import com.chronos.ai.model.AiModelResponse;
import com.chronos.ai.service.AiModelService;
import com.chronos.commons.model.ResultData;

import jakarta.validation.Valid;

@RestController
public class AiModelController {
	private final AiModelService service;

	public AiModelController(AiModelService service) {
		this.service = service;
	}

	@GetMapping("/ai-model/models/list")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:view','ai:model:manage')")
	public ResultData<?> list(
			@RequestParam(required = false) String modelName,
			@RequestParam(required = false) String provider,
			@RequestParam(required = false) Integer status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ok(service.list(modelName, provider, status, page, size).map(AiModelResponse::from));
	}

	@GetMapping("/ai-model/models/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:view','ai:model:manage')")
	public ResultData<AiModelResponse> detail(@PathVariable String id) {
		return ok(AiModelResponse.from(service.get(id)));
	}

	@PostMapping("/ai-model/models")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:create','ai:model:manage')")
	public ResultData<AiModelResponse> create(@Valid @RequestBody AiModel command) {
		return ok(AiModelResponse.from(service.create(command)));
	}

	@PutMapping("/ai-model/models")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:update','ai:model:manage')")
	public ResultData<AiModelResponse> update(@Valid @RequestBody AiModel command) {
		return ok(AiModelResponse.from(service.update(command)));
	}

	@DeleteMapping("/ai-model/models/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'ai:model:delete','ai:model:manage')")
	public ResultData<Void> delete(@PathVariable String id) {
		service.delete(id);
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
