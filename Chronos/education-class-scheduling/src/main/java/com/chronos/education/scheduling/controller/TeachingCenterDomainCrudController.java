package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.TeachingDomainService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.chronos.education.scheduling.service.TeachingReviewService;

/** 九类教学领域的统一、稳定 CRUD 契约；type 只接受服务白名单。 */
@RestController
@RequestMapping("/education/teaching-center/domain")
public class TeachingCenterDomainCrudController {
	private final TeachingDomainService service;
	private final TeachingReviewService reviews;
	public TeachingCenterDomainCrudController(TeachingDomainService service, TeachingReviewService reviews) { this.service = service; this.reviews = reviews; }

	@PostMapping("/{type}/{id}/submit-review")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:update','education:teaching:manage')")
	public ResultData<?> submitReview(@PathVariable String type, @PathVariable String id,
			@RequestBody(required=false) Map<String,Object> body, Authentication authentication) {
		Object resource = service.get(type, id, authentication);
		return ok(reviews.submit(type, id, service.offeringId(resource), body, authentication));
	}

	@GetMapping("/{type}/{id}/review-status")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<?> reviewStatus(@PathVariable String type, @PathVariable String id, Authentication authentication) {
		return ok(reviews.status(type, id, authentication));
	}

	@GetMapping("/{type}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<PageView<?>> page(@PathVariable String type,
			@RequestParam(required = false) String offeringId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		return ok(service.page(type, offeringId, page, size, authentication));
	}

	@PostMapping("/{type}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:manage')")
	public ResultData<?> create(@PathVariable String type, @RequestBody Map<String,Object> body,
			Authentication authentication) { return ok(service.create(type, body, authentication)); }

	@PutMapping("/{type}/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> update(@PathVariable String type, @PathVariable String id,
			@RequestBody Map<String,Object> body, Authentication authentication) {
		return ok(service.update(type, id, body, authentication));
	}

	@PostMapping("/{type}/{id}/archive")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> archive(@PathVariable String type, @PathVariable String id,
			Authentication authentication) { return ok(service.archive(type, id, authentication)); }

	@PostMapping("/{type}/{id}/status")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> status(@PathVariable String type, @PathVariable String id,
			@RequestParam String status, Authentication authentication) {
		return ok(service.status(type, id, status, authentication));
	}

	@GetMapping("/{type}/export")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<List<?>> export(@PathVariable String type,
			@RequestParam(required = false) String offeringId, Authentication authentication) {
		return ok(service.exportData(type, offeringId, authentication));
	}

	@GetMapping(value = "/{type}/export.csv", produces = "text/csv")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public String exportCsv(@PathVariable String type, @RequestParam(required = false) String offeringId,
			Authentication authentication) {
		return service.exportCsv(type, offeringId, authentication);
	}

	@PostMapping("/{type}/import")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:manage')")
	public ResultData<List<?>> importData(@PathVariable String type,
			@RequestBody List<Map<String,Object>> body, Authentication authentication) {
		return ok(body.stream().map(item -> service.create(type, item, authentication)).toList());
	}

	@PostMapping(value = "/{type}/import.csv", consumes = "text/csv")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:manage')")
	public ResultData<List<?>> importCsv(@PathVariable String type, @RequestBody String body,
			Authentication authentication) {
		return ok(service.importCsv(type, body, authentication));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}
