package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.dto.TeachingPlanDtos.*;
import com.chronos.education.scheduling.service.TeachingPlanLessonService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Business APIs; the legacy type/map API is intentionally not used for this slice. */
@RestController
@RequestMapping("/education/teaching")
public class TeachingPlanLessonController {
	private final TeachingPlanLessonService service;
	public TeachingPlanLessonController(TeachingPlanLessonService service) { this.service = service; }
	@GetMapping("/context")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:view','education:teaching:lesson:view','education:teaching:manage')")
	public ResultData<?> context(@RequestParam(required=false) String semesterId, Authentication a) { return ok(service.context(semesterId, a)); }

	@PostMapping("/plans")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:create','education:teaching:manage')")
	public ResultData<?> createPlan(@Valid @RequestBody PlanCreateRequest request, Authentication a) { return ok(service.createPlan(request, a)); }
	@PutMapping("/plans/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:update','education:teaching:manage')")
	public ResultData<?> updatePlan(@PathVariable String id, @Valid @RequestBody PlanUpdateRequest request, Authentication a) { return ok(service.updatePlan(id, request, a)); }
	@GetMapping("/plans/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:view','education:teaching:manage')")
	public ResultData<?> plan(@PathVariable String id, Authentication a) { return ok(Map.of("plan", service.getPlan(id, a), "items", service.planItems(id, a))); }
	@GetMapping("/plans/{id}/versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:view','education:teaching:manage')")
	public ResultData<?> planVersions(@PathVariable String id, Authentication a) { return ok(service.planVersions(id, a)); }
	@PostMapping("/plans/{id}/submit")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:submit','education:teaching:manage')")
	public ResultData<?> submitPlan(@PathVariable String id, @RequestHeader(value="Idempotency-Key", required=false) String key, Authentication a) { return ok(service.submitPlan(id, key, a)); }
	@PostMapping("/plans/{id}/revise")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:plan:update','education:teaching:manage')")
	public ResultData<?> revisePlan(@PathVariable String id, @RequestParam Long rowVersion, Authentication a) { return ok(service.revisePlan(id, rowVersion, a)); }

	@PostMapping("/lessons")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:lesson:create','education:teaching:manage')")
	public ResultData<?> createLesson(@Valid @RequestBody LessonCreateRequest request, Authentication a) { return ok(service.createLesson(request, a)); }
	@PutMapping("/lessons/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:lesson:update','education:teaching:manage')")
	public ResultData<?> updateLesson(@PathVariable String id, @Valid @RequestBody LessonUpdateRequest request, Authentication a) { return ok(service.updateLesson(id, request, a)); }
	@GetMapping("/lessons/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:lesson:view','education:teaching:manage')")
	public ResultData<?> lesson(@PathVariable String id, Authentication a) { return ok(service.getLesson(id, a)); }
	@GetMapping("/lessons/{id}/versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:lesson:view','education:teaching:manage')")
	public ResultData<?> lessonVersions(@PathVariable String id, Authentication a) { return ok(service.lessonVersions(id, a)); }
	@PostMapping("/lessons/{id}/submit")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:lesson:submit','education:teaching:manage')")
	public ResultData<?> submitLesson(@PathVariable String id, @RequestHeader(value="Idempotency-Key", required=false) String key, Authentication a) { return ok(service.submitLesson(id, key, a)); }

	private <T> ResultData<T> ok(T data) { return ResultData.<T>builder().code("200").msg("success").data(data).build(); }
}
