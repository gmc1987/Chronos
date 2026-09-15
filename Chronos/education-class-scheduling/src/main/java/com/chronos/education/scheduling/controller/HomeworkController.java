package com.chronos.education.scheduling.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.HomeworkAssignment;
import com.chronos.education.scheduling.model.HomeworkSubmission;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.AssignmentRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.GradeRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.SubmissionRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.BatchGradeRequest;
import com.chronos.education.scheduling.service.HomeworkService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/education/teaching-center/api")
public class HomeworkController {
	private final HomeworkService service;

	public HomeworkController(HomeworkService service) {
		this.service = service;
	}

	@GetMapping("/homeworks")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:view','education:homework:manage')")
	public ResultData<PageView<HomeworkAssignment>> assignments(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String offeringId, Authentication auth) {
		return ok(service.pageAssignments(page, size, offeringId, auth));
	}

	@GetMapping("/homeworks/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:view','education:homework:manage')")
	public ResultData<HomeworkAssignment> assignment(@PathVariable String id, Authentication auth) {
		return ok(service.get(id, auth));
	}

	@PostMapping("/homeworks")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:create','education:homework:manage')")
	public ResultData<HomeworkAssignment> create(@Valid @RequestBody AssignmentRequest request,
			Authentication auth) {
		return ok(service.create(request, auth));
	}

	@PutMapping("/homeworks/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:update','education:homework:manage')")
	public ResultData<HomeworkAssignment> update(@PathVariable String id,
			@Valid @RequestBody AssignmentRequest request, Authentication auth) {
		return ok(service.update(id, request, auth));
	}

	@PostMapping("/homeworks/{id}/publish")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:publish','education:homework:manage')")
	public ResultData<HomeworkAssignment> publish(@PathVariable String id, Authentication auth) {
		return ok(service.publish(id, auth));
	}

	@PostMapping("/homeworks/{id}/close")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:close','education:homework:manage')")
	public ResultData<HomeworkAssignment> close(@PathVariable String id, Authentication auth) {
		return ok(service.close(id, auth));
	}

	@PostMapping("/homeworks/{id}/archive")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:archive','education:homework:manage')")
	public ResultData<HomeworkAssignment> archive(@PathVariable String id, Authentication auth) {
		return ok(service.archive(id, auth));
	}

	@GetMapping("/homeworks/{id}/submissions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:view','education:homework:manage')")
	public ResultData<PageView<HomeworkSubmission>> submissions(@PathVariable String id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			Authentication auth) {
		return ok(service.submissions(id, page, size, auth));
	}

	@GetMapping("/homeworks/{id}/my-submission")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:view','education:homework:view','education:homework:manage')")
	public ResultData<HomeworkSubmission> mySubmission(@PathVariable String id, Authentication auth) {
		return ok(service.mySubmission(id, auth));
	}

	@PostMapping("/homeworks/{assignmentId}/submissions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:create','education:homework:view','education:homework:manage')")
	public ResultData<HomeworkSubmission> saveDraft(@PathVariable String assignmentId,
			@Valid @RequestBody SubmissionRequest request, Authentication auth) {
		return ok(service.saveDraft(assignmentId, request, auth));
	}

	@PutMapping("/homework-submissions/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:update','education:homework:view','education:homework:manage')")
	public ResultData<HomeworkSubmission> updateDraft(@PathVariable String id,
			@Valid @RequestBody SubmissionRequest request, Authentication auth) {
		return ok(service.updateDraft(id, request, auth));
	}

	@PostMapping("/homework-submissions/{id}/submit")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:submit','education:homework:view','education:homework:manage')")
	public ResultData<HomeworkSubmission> submit(@PathVariable String id, Authentication auth) {
		return ok(service.submit(id, auth));
	}

	@PostMapping("/homework-submissions/{id}/grade")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:grade','education:homework:manage')")
	public ResultData<HomeworkSubmission> grade(@PathVariable String id,
			@Valid @RequestBody GradeRequest request, Authentication auth) {
		return ok(service.grade(id, request, auth));
	}

	@PostMapping("/homeworks/{id}/batch-grade")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:grade','education:homework:manage')")
	public ResultData<java.util.List<HomeworkSubmission>> batchGrade(@PathVariable String id,
			@Valid @RequestBody BatchGradeRequest request, Authentication auth) {
		return ok(service.batchGrade(id, request, auth));
	}

	@PostMapping("/homeworks/{id}/publish-grades")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:submission:publish','education:homework:manage')")
	public ResultData<Integer> publishGrades(@PathVariable String id, Authentication auth) {
		return ok(service.publishGrades(id, auth));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
