package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.dto.GradeProductionDtos.*;
import com.chronos.education.grade.model.GradeChangeRequest;
import com.chronos.education.grade.model.MakeupExamRecord;
import com.chronos.education.grade.service.GradeProductionService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/education/grade-production")
public class GradeProductionController {
	private final GradeProductionService service;

	public GradeProductionController(GradeProductionService service) {
		this.service = service;
	}

	@GetMapping("/gradebooks/{id}/changes")
	@PreAuthorize("@iamAuthorization.any(authentication, 'education:score:change:request', 'education:score:change:approve')")
	public ResultData<List<GradeChangeRequest>> changes(@PathVariable String id, Authentication authentication) {
		return ok(service.changes(id, authentication.getName()));
	}

	@GetMapping("/gradebooks/{id}/published-grades")
	@PreAuthorize("hasAuthority('education:score:gradebook:view')")
	public ResultData<List<PublishedGradeView>> publishedGrades(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.publishedGrades(id, authentication.getName()));
	}

	@PostMapping("/gradebooks/{id}/changes")
	@PreAuthorize("hasAuthority('education:score:change:request')")
	public ResultData<GradeChangeRequest> requestChange(
			@PathVariable String id,
			@RequestBody ChangeRequestCommand command,
			Authentication authentication) {
		return ok(service.requestChange(id, command, authentication.getName()));
	}

	@PostMapping("/changes/{id}/decision")
	@PreAuthorize("hasAuthority('education:score:change:approve')")
	public ResultData<GradeChangeRequest> decide(
			@PathVariable String id,
			@RequestBody ChangeDecisionCommand command,
			Authentication authentication) {
		return ok(service.decideChange(id, command, authentication.getName()));
	}

	@GetMapping("/gradebooks/{id}/makeups")
	@PreAuthorize("hasAuthority('education:score:gradebook:view')")
	public ResultData<List<MakeupExamRecord>> makeups(@PathVariable String id, Authentication authentication) {
		return ok(service.makeups(id, authentication.getName()));
	}

	@PostMapping("/gradebooks/{id}/makeups")
	@PreAuthorize("hasAuthority('education:score:makeup:manage')")
	public ResultData<MakeupExamRecord> register(
			@PathVariable String id,
			@RequestBody MakeupRegisterCommand command,
			Authentication authentication) {
		return ok(service.registerMakeup(id, command, authentication.getName()));
	}

	@PutMapping("/makeups/{id}/result")
	@PreAuthorize("hasAuthority('education:score:makeup:manage')")
	public ResultData<MakeupExamRecord> result(
			@PathVariable String id,
			@RequestBody MakeupResultCommand command,
			Authentication authentication) {
		return ok(service.saveMakeupResult(id, command, authentication.getName()));
	}

	@PostMapping("/makeups/{id}/publish")
	@PreAuthorize("hasAuthority('education:score:makeup:manage')")
	public ResultData<MakeupExamRecord> publish(@PathVariable String id, Authentication authentication) {
		return ok(service.publishMakeup(id, authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
