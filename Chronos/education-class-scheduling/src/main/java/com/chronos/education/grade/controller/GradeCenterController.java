package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.dto.GradeDtos.GradebookCommand;
import com.chronos.education.grade.dto.GradeDtos.GradebookDetailResponse;
import com.chronos.education.grade.dto.GradeDtos.ItemsCommand;
import com.chronos.education.grade.dto.GradeDtos.OfferingOption;
import com.chronos.education.grade.dto.GradeDtos.SchemeCommand;
import com.chronos.education.grade.dto.GradeDtos.SchemeDetailResponse;
import com.chronos.education.grade.model.AssessmentScheme;
import com.chronos.education.grade.model.GradePublishSnapshot;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.service.GradeCenterService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/education/grades")
public class GradeCenterController {
	private final GradeCenterService service;

	public GradeCenterController(GradeCenterService service) {
		this.service = service;
	}

	@GetMapping("/schemes")
	@PreAuthorize("hasAuthority('education:score:scheme:view')")
	public ResultData<List<AssessmentScheme>> schemes(
			@RequestParam(required = false) String offeringId,
			Authentication authentication) {
		return ok(service.listSchemes(offeringId, authentication.getName()));
	}

	@GetMapping("/offerings")
	@PreAuthorize("@iamAuthorization.any(authentication, 'education:score:scheme:view', "
			+ "'education:score:scheme:create', 'education:score:gradebook:view', "
			+ "'education:score:gradebook:create')")
	public ResultData<List<OfferingOption>> offerings(Authentication authentication) {
		return ok(service.availableOfferings(authentication.getName()));
	}

	@GetMapping("/schemes/{id}")
	@PreAuthorize("hasAuthority('education:score:scheme:view')")
	public ResultData<SchemeDetailResponse> scheme(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.getScheme(id, authentication.getName()));
	}

	@PostMapping("/schemes")
	@PreAuthorize("hasAuthority('education:score:scheme:create')")
	public ResultData<AssessmentScheme> create(@RequestBody SchemeCommand command, Authentication authentication) {
		return ok(service.createScheme(command, authentication.getName()));
	}

	@PutMapping("/schemes/{id}")
	@PreAuthorize("hasAuthority('education:score:scheme:update')")
	public ResultData<AssessmentScheme> update(
			@PathVariable String id,
			@RequestBody SchemeCommand command,
			Authentication authentication) {
		return ok(service.updateScheme(id, command, authentication.getName()));
	}

	@PostMapping("/schemes/{id}/publish")
	@PreAuthorize("hasAuthority('education:score:scheme:publish')")
	public ResultData<AssessmentScheme> publishScheme(@PathVariable String id, Authentication authentication) {
		return ok(service.publishScheme(id, authentication.getName()));
	}

	@GetMapping("/gradebooks/{id}")
	@PreAuthorize("hasAuthority('education:score:gradebook:view')")
	public ResultData<GradebookDetailResponse> gradebook(@PathVariable String id, Authentication authentication) {
		return ok(service.getGradebook(id, authentication.getName()));
	}

	@GetMapping("/gradebooks/{id}/snapshots")
	@PreAuthorize("hasAuthority('education:score:gradebook:view')")
	public ResultData<List<GradePublishSnapshot>> snapshots(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.snapshots(id, authentication.getName()));
	}

	@GetMapping("/gradebooks")
	@PreAuthorize("hasAuthority('education:score:gradebook:view')")
	public ResultData<List<Gradebook>> gradebooks(Authentication authentication) {
		return ok(service.listGradebooks(authentication.getName()));
	}

	@PostMapping("/gradebooks")
	@PreAuthorize("hasAuthority('education:score:gradebook:create')")
	public ResultData<Gradebook> createGradebook(
			@RequestBody GradebookCommand command,
			Authentication authentication) {
		return ok(service.createGradebook(command, authentication.getName()));
	}

	@PutMapping("/gradebooks/{id}/items")
	@PreAuthorize("hasAuthority('education:score:gradebook:update')")
	public ResultData<Gradebook> items(
			@PathVariable String id,
			@RequestBody ItemsCommand command,
			Authentication authentication) {
		return ok(service.saveItems(id, command, authentication.getName()));
	}

	@PostMapping("/gradebooks/{id}/submit")
	@PreAuthorize("hasAuthority('education:score:gradebook:submit')")
	public ResultData<Gradebook> submit(@PathVariable String id, Authentication authentication) {
		return ok(service.submit(id, authentication.getName()));
	}

	@PostMapping("/gradebooks/{id}/review")
	@PreAuthorize("hasAuthority('education:score:review')")
	public ResultData<Gradebook> review(
			@PathVariable String id,
			@RequestParam String taskId,
			@RequestParam boolean approved,
			@RequestParam(required = false) String comment,
			Authentication authentication) {
		return ok(service.review(id, taskId, approved, comment, authentication.getName()));
	}

	@PostMapping("/gradebooks/{id}/publish")
	@PreAuthorize("hasAuthority('education:score:gradebook:publish')")
	public ResultData<Gradebook> publish(@PathVariable String id, Authentication authentication) {
		// 发布事件由业务事务内的 Outbox 唯一写入，避免控制器再次发布导致重复通知。
		return ok(service.publish(id, authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
