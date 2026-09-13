package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.TeachingDomainService;
import com.chronos.education.scheduling.service.TeachingReviewService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Legacy read-only compatibility mapping for the former generic domain API.
 * All writes belong to the explicit domain controllers under {@code /api}.
 */
@RestController
@RequestMapping("/education/teaching-center/domain")
public class TeachingCenterDomainCrudController {
	private final TeachingDomainService service;
	private final TeachingReviewService reviews;
	public TeachingCenterDomainCrudController(TeachingDomainService service, TeachingReviewService reviews) { this.service = service; this.reviews = reviews; }

	@GetMapping("/{type}/{id}/review-status")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<?> reviewStatus(@PathVariable String type, @PathVariable String id, Authentication authentication) {
		return ok(reviews.status(type, id, authentication));
	}

	@GetMapping("/{type}/{id}/review-history")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:review','education:teaching:manage')")
	public ResultData<?> reviewHistory(@PathVariable String type, @PathVariable String id,
			Authentication authentication) {
		return ok(reviews.history(type, id, authentication));
	}

	@PostMapping("/{type}/{id}/revise")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> revise(@PathVariable String type, @PathVariable String id,
			Authentication authentication) {
		return ok(reviews.revise(type, id, authentication));
	}

	@GetMapping("/review-todos")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:review','education:teaching:manage')")
	public ResultData<?> reviewTodos(Authentication authentication) {
		return ok(reviews.pending(authentication));
	}

	@GetMapping("/{type}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<PageView<?>> page(@PathVariable String type,
			@RequestParam(required = false) String offeringId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		return ok(service.page(type, offeringId, page, size, authentication));
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

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}
