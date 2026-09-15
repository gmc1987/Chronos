package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.ResearchErrorService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/teaching-center")
public class ResearchErrorController {
	private final ResearchErrorService service;
	public ResearchErrorController(ResearchErrorService service) { this.service=service; }
	@GetMapping("/research-groups")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:view','education:teaching:manage')")
	public ResultData<?> groups(Authentication a) { return ok(service.groups(a)); }
	@GetMapping("/research-groups/{id}/activities")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:view','education:teaching:manage')")
	public ResultData<?> activities(@PathVariable String id, Authentication a) { return ok(service.activities(id, a)); }
	@GetMapping("/research-groups/{id}/members")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:view','education:teaching:manage')")
	public ResultData<?> groupMembers(@PathVariable String id, Authentication a) { return ok(service.groupMembers(id, a)); }
	@DeleteMapping("/research-groups/{id}/members/{teacherId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> removeGroupMember(@PathVariable String id, @PathVariable String teacherId, Authentication a) {
		service.removeMember(id, teacherId, a); return ok(Boolean.TRUE);
	}
	@GetMapping("/research-activities/{id}/results")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:view','education:teaching:manage')")
	public ResultData<?> results(@PathVariable String id, Authentication a) { return ok(service.results(id, a)); }
	@GetMapping("/research-activities/{id}/members")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:view','education:teaching:manage')")
	public ResultData<?> activityMembers(@PathVariable String id, Authentication a) { return ok(service.activityMembers(id, a)); }
	@DeleteMapping("/research-activities/{id}/members/{teacherId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> removeActivityMember(@PathVariable String id, @PathVariable String teacherId, Authentication a) {
		service.removeActivityMember(id, teacherId, a); return ok(Boolean.TRUE);
	}
	@GetMapping("/research-activities/{id}/materials")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:view','education:teaching:manage')")
	public ResultData<?> materials(@PathVariable String id, Authentication a) { return ok(service.materials(id, a)); }
	@PutMapping("/research-groups/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> updateGroup(@PathVariable String id, @Valid @RequestBody GroupRequest r, Authentication a) { return ok(service.updateGroup(id, r, a)); }
	@PutMapping("/research-activities/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> updateActivity(@PathVariable String id, @Valid @RequestBody ActivityRequest r, Authentication a) { return ok(service.updateActivity(id, r, a)); }
	@PostMapping("/research-activities/{id}/cancel")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> cancelActivity(@PathVariable String id, @Valid @RequestBody ActivityCancelRequest r, Authentication a) {
		return ok(service.cancelActivity(id, r, a));
	}
	@PutMapping("/research-results/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> updateResult(@PathVariable String id, @Valid @RequestBody ResultRequest r, Authentication a) { return ok(service.updateResult(id, r, a)); }
	@PostMapping("/research-groups")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:create','education:teaching:manage')")
	public ResultData<?> group(@Valid @RequestBody GroupRequest r, Authentication a) { return ok(service.createGroup(r,a)); }
	@PostMapping("/research-groups/{id}/members")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> member(@PathVariable String id,@Valid @RequestBody MemberRequest r,Authentication a) { return ok(service.addMember(id,r,a)); }
	@PostMapping("/research-groups/{id}/activities")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:create','education:teaching:manage')")
	public ResultData<?> activity(@PathVariable String id,@Valid @RequestBody ActivityRequest r,Authentication a) { return ok(service.createActivity(id,r,a)); }
	@PostMapping("/research-activities/{id}/members")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> activityMember(@PathVariable String id,@Valid @RequestBody MemberRequest r,Authentication a) { return ok(service.inviteActivityMember(id,r,a)); }
	@PostMapping("/research-activities/{id}/attendance")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> attendance(@PathVariable String id,@Valid @RequestBody AttendanceRequest r,Authentication a) { return ok(service.attendance(id,r,a)); }
	@PostMapping("/research-activities/{id}/minutes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> minutes(@PathVariable String id,@Valid @RequestBody MinutesRequest r,Authentication a) { return ok(service.updateMinutes(id,r,a)); }
	@PostMapping("/research-activities/{id}/materials")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> material(@PathVariable String id,@Valid @RequestBody MaterialRequest r,Authentication a) { return ok(service.addMaterial(id,r,a)); }
	@PostMapping("/research-activities/{id}/results")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:create','education:teaching:manage')")
	public ResultData<?> result(@PathVariable String id,@Valid @RequestBody ResultRequest r,Authentication a) { return ok(service.addResult(id,r,a)); }
	@PostMapping("/research-results/{id}/submit")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> submit(@PathVariable String id,Authentication a) { return ok(service.submitResult(id,a)); }
	@PostMapping("/research-results/{id}/archive")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:research:update','education:teaching:manage')")
	public ResultData<?> archive(@PathVariable String id, Authentication a) {
		return ok(service.transitionResult(id, "ARCHIVED", a));
	}
	@PostMapping("/error-books/manual")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:error-book:create','education:teaching:manage')")
	public ResultData<?> manual(@Valid @RequestBody ErrorManualRequest r,Authentication a) { return ok(service.recordManual(r,a)); }
	@GetMapping("/error-books/items")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:error-book:view','education:teaching:manage')")
	public ResultData<?> items(@RequestParam(required = false) String courseId, Authentication a) {
		return ok(service.items(courseId, a));
	}
	@PostMapping("/error-books/wrong-answer-confirmed")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:error-book:create','education:teaching:manage')")
	public ResultData<?> confirmed(@Valid @RequestBody WrongAnswerConfirmed r,Authentication a) { return ok(service.onWrongAnswerConfirmed(r,a)); }
	@PostMapping("/error-items/{id}/mastery")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:error-book:update','education:teaching:manage')")
	public ResultData<?> mastery(@PathVariable String id,@Valid @RequestBody MasteryRequest r,Authentication a) { return ok(service.mastery(id,r,a)); }
	@PostMapping("/error-items/{id}/review")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:error-book:update','education:teaching:manage')")
	public ResultData<?> review(@PathVariable String id, @Valid @RequestBody ErrorReviewRequest r, Authentication a) {
		return ok(service.review(id, r, a));
	}
	private <T> ResultData<T> ok(T value) { return ResultData.<T>builder().code("200").msg("success").data(value).build(); }
}
