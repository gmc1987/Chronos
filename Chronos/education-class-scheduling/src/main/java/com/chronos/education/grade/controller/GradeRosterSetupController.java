package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.service.GradeRosterSetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/education/grades/roster-setup")
public class GradeRosterSetupController {
	private final GradeRosterSetupService service;

	private static final String SETUP = "@iamAuthorization.has(authentication,'education:score:gradebook:create')";

	@GetMapping("/special-statuses")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:score:gradebook:view')")
	public ResultData<java.util.List<GradeRosterSetupService.Option>> statuses() {
		return ok(service.specialStatuses());
	}

	@PostMapping("/teachers")
	@PreAuthorize(SETUP)
	public ResultData<GradeRosterSetupService.Option> teacher(@RequestParam String schoolId, @RequestBody Teacher body,
			Authentication actor) {
		return ok(service.registerTeacher(schoolId, body.code(), body.name(), actor.getName()));
	}

	@PostMapping("/student-links")
	@PreAuthorize(SETUP)
	public ResultData<Void> linkStudent(@RequestParam String schoolId, @RequestBody StudentLink body, Authentication actor) {
		service.linkStudent(schoolId, body.studentNo(), body.username(), actor.getName());
		return ok(null);
	}

	public record Teacher(String code, String name) {}
	public record StudentLink(String studentNo, String username) {}

	@GetMapping
	@PreAuthorize(SETUP)
	public ResultData<GradeRosterSetupService.Options> options(@RequestParam String schoolId, Authentication actor) {
		return ok(service.options(schoolId, actor.getName()));
	}

	@PostMapping
	@PreAuthorize(SETUP)
	public ResultData<GradeRosterSetupService.Result> create(@RequestParam String schoolId,
			@RequestBody GradeRosterSetupService.Command command, Authentication actor) {
		return ok(service.create(schoolId, command, actor.getName()));
	}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
