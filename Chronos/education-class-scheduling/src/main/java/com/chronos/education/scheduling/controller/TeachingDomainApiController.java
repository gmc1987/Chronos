package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.TeachingDomainCommand;
import com.chronos.education.scheduling.service.TeachingDomainApiService;
import com.chronos.education.scheduling.service.TeachingChildService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Explicit teaching-center domain API. It intentionally does not replace
 * /domain/{type}, which is retained as a compatibility/import endpoint.
 */
@RestController
@RequestMapping("/education/teaching-center/api")
public class TeachingDomainApiController {
	private final TeachingDomainApiService service;
	private final TeachingChildService children;

	public TeachingDomainApiController(TeachingDomainApiService service, TeachingChildService children) {
		this.service = service;
		this.children = children;
	}

	@GetMapping("/{domain:plans|lesson-plans|preparations|coursewares|materials|question-banks|questions|knowledge-points|mistakes|research}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<PageView<?>> page(@PathVariable String domain,
			@RequestParam(required = false) String offeringId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication user) {
		return ok(service.page(domain, offeringId, page, size, user));
	}

	@GetMapping("/{domain:plans|lesson-plans|preparations|coursewares|materials|question-banks|questions|knowledge-points|mistakes|research}/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<?> detail(@PathVariable String domain, @PathVariable String id, Authentication user) {
		return ok(service.detail(domain, id, user));
	}

	@PostMapping("/{domain:plans|lesson-plans|preparations|coursewares|materials|question-banks|questions|knowledge-points|mistakes|research}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:create','education:teaching:manage')")
	public ResultData<?> create(@PathVariable String domain, @RequestBody TeachingDomainCommand command,
			Authentication user) {
		return ok(service.create(domain, command, user));
	}

	@PutMapping("/{domain:plans|lesson-plans|preparations|coursewares|materials|question-banks|questions|knowledge-points|mistakes|research}/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> update(@PathVariable String domain, @PathVariable String id,
			@RequestBody TeachingDomainCommand command, Authentication user) {
		return ok(service.update(domain, id, command, user));
	}

	@PostMapping("/{domain:plans|lesson-plans|preparations|coursewares|materials|question-banks|questions|knowledge-points|mistakes|research}/{id}/archive")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> archive(@PathVariable String domain, @PathVariable String id, Authentication user) {
		return ok(service.archive(domain, id, user));
	}

	@PostMapping("/{domain:plans|lesson-plans|preparations|coursewares|materials|question-banks|questions|knowledge-points|mistakes|research}/{id}/status")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:update','education:teaching:manage')")
	public ResultData<?> transition(@PathVariable String domain, @PathVariable String id,
			@RequestParam String status, Authentication user) {
		return ok(service.transition(domain, id, status, user));
	}

	/** Frequently used aggregate children have named routes as well. */
	@GetMapping("/{domain:plans|lesson-plans|preparations|questions|research}/{id}/{child:items|versions|materials|options|activities}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')")
	public ResultData<PageView<?>> children(@PathVariable String domain, @PathVariable String id,
			@PathVariable String child, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size, Authentication user) {
		String type = switch (domain + "/" + child) {
			case "plans/items" -> "PLAN_ITEM";
			case "plans/versions" -> "PLAN_VERSION";
			case "lesson-plans/versions" -> "LESSON_VERSION";
			case "preparations/materials" -> "PREPARATION_MATERIAL";
			case "questions/options" -> "QUESTION_OPTION";
			case "research/activities" -> "RESEARCH_ACTIVITY_MEMBER";
			default -> throw new IllegalArgumentException("不支持的教学子对象");
		};
		return ok(children.page(type, id, page, size, user));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}
