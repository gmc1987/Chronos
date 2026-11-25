package com.chronos.facility.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.facility.service.FacilityAssetLifecycleService;
import com.chronos.facility.service.FacilityAssetLifecycleService.Decision;
import com.chronos.facility.service.FacilityAssetLifecycleService.Request;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/facility/asset-lifecycle")
public class FacilityAssetLifecycleController {
	private final FacilityAssetLifecycleService service;

	public FacilityAssetLifecycleController(FacilityAssetLifecycleService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:asset:view','facility:asset:manage','facility:asset-lifecycle:review')")
	public ResultData<List<Map<String, Object>>> list(@RequestParam(required = false) String organizationId,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "10") int limit,
			Authentication authentication) {
		return ok(service.list(organizationId, offset, limit, authentication));
	}

	@GetMapping("/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:asset:view','facility:asset:manage','facility:asset-lifecycle:review')")
	public ResultData<Map<String, Object>> detail(@PathVariable String id, Authentication authentication) {
		return ok(service.detail(id, authentication));
	}

	@PostMapping
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:asset:manage')")
	public ResultData<String> submit(@RequestBody Request request, Authentication authentication) {
		return ok(service.submit(request, authentication));
	}

	@PostMapping("/{id}/decisions")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:asset-lifecycle:review')")
	public ResultData<Boolean> decide(@PathVariable String id, @RequestBody Decision decision,
			Authentication authentication) {
		service.decide(id, decision, authentication);
		return ok(true);
	}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("操作成功").data(data).build();
	}
}
