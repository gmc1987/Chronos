package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.TeachingCenterResource;
import com.chronos.education.scheduling.model.TeachingCenterResourceCommand;
import com.chronos.education.scheduling.service.TeachingCenterService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/teaching-center")
public class TeachingCenterController {
	private final TeachingCenterService service;

	public TeachingCenterController(TeachingCenterService service) {
		this.service = service;
	}

	@GetMapping("/resources")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<PageView<TeachingCenterResource>> page(
			@RequestParam String type, @RequestParam(required = false) String offeringId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		return ok(service.page(type, offeringId, page, size, authentication));
	}

	@PostMapping("/resources")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:manage')")
	public ResultData<TeachingCenterResource> create(
			@Valid @RequestBody TeachingCenterResourceCommand command,
			Authentication authentication) {
		return ok(service.create(command, authentication));
	}

	@PutMapping("/resources/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<TeachingCenterResource> update(
			@PathVariable String id, @Valid @RequestBody TeachingCenterResourceCommand command,
			Authentication authentication) {
		return ok(service.update(id, command, authentication));
	}

	@PostMapping("/resources/{id}/status")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<TeachingCenterResource> transition(
			@PathVariable String id, @RequestParam String status,
			Authentication authentication) {
		return ok(service.transition(id, status, authentication));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}
