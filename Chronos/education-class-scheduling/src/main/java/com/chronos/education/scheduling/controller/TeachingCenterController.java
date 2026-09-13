package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.TeachingCenterResource;
import com.chronos.education.scheduling.model.TeachingCenterResourceCommand;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.TeachingCenterService;
import com.chronos.education.scheduling.service.TeachingReviewService;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/teaching-center")
public class TeachingCenterController {
	private final TeachingCenterService service;
	private final TeachingReviewService reviews;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;

	public TeachingCenterController(
			TeachingCenterService service,
			TeachingReviewService reviews,
			CourseOfferingRepository offerings,
			EducationDataScopeService scopes) {
		this.service = service;
		this.reviews = reviews;
		this.offerings = offerings;
		this.scopes = scopes;
	}

	/** 门户只返回当前用户数据范围内的教学班，避免让教师手填内部 ID。 */
	@GetMapping("/offerings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<List<CourseOffering>> visibleOfferings(Authentication authentication) {
		return ok(scopes.visibleOfferings(
				scopes.resolve(authentication.getName()),
				offerings.findAll()));
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

	@PostMapping("/resources/{id}/submit-review")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:update','education:teaching:manage')")
	public ResultData<?> submitReview(@PathVariable String id, @RequestBody(required=false) Map<String,Object> body,
			Authentication authentication) {
		TeachingCenterResource resource = service.get(id, authentication);
		return ok(reviews.submit(resource.getResourceType(), id, resource.getOfferingId(), body, authentication));
	}

	@GetMapping("/resources/{id}/review-status")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<?> reviewStatus(@PathVariable String id, Authentication authentication) {
		TeachingCenterResource resource = service.get(id, authentication);
		return ok(reviews.status(resource.getResourceType(), id, authentication));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}
