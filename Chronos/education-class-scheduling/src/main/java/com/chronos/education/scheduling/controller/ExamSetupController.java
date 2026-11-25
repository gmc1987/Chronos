package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.service.ExamSetupService;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/education/exam/setup")
public class ExamSetupController {
	private static final String VIEW = "@iamAuthorization.any(authentication,'education:exam:plan:view',"
			+ "'education:exam:plan:manage','education:exam:room:manage','education:exam:invigilation:view',"
			+ "'education:exam:invigilation:manage','education:exam:paper-analysis:view','education:exam:paper-analysis:manage')";
	private static final String MANAGE = "@iamAuthorization.has(authentication,'education:exam:plan:manage')";
	private final ExamSetupService service;

	@GetMapping
	@PreAuthorize(VIEW)
	public ResultData<ExamSetupService.Lookups> options(@RequestParam String schoolId, Authentication actor) {
		return ok(service.lookups(schoolId, actor.getName()));
	}

	@GetMapping("/students")
	@PreAuthorize(VIEW)
	public ResultData<ExamSetupService.StudentPage> students(@RequestParam String schoolId,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			Authentication actor) {
		return ok(service.students(schoolId, keyword, page, actor.getName()));
	}

	@GetMapping("/student-names")
	@PreAuthorize(VIEW)
	public ResultData<List<ExamSetupService.StudentOption>> studentNames(@RequestParam String schoolId,
			@RequestParam String ids, Authentication actor) {
		return ok(service.studentNames(schoolId, Arrays.stream(ids.split(",")).distinct().toList(), actor.getName()));
	}

	@PostMapping("/terms")
	@PreAuthorize(MANAGE)
	public ResultData<AcademicTerm> createTerm(@RequestParam String schoolId,
			@RequestBody SchoolRosterSetupService.TermCommand command, Authentication actor) {
		return ok(service.createTerm(schoolId, command, actor.getName()));
	}

	@PostMapping("/roster")
	@PreAuthorize(MANAGE)
	public ResultData<SchoolRosterSetupService.RosterResult> importRoster(@RequestParam String schoolId,
			@RequestBody List<SchoolRosterSetupService.RosterRow> rows, Authentication actor) {
		return ok(service.importStudents(schoolId, rows, actor.getName()));
	}

	@PostMapping("/resources/{kind}")
	@PreAuthorize(MANAGE)
	public ResultData<ExamSetupService.Option> createResource(@RequestParam String schoolId,
			@PathVariable String kind, @RequestBody ExamSetupService.Resource command, Authentication actor) {
		return ok(service.createResource(schoolId, kind, command, actor.getName()));
	}

	@PostMapping("/account-links")
	@PreAuthorize(MANAGE)
	public ResultData<Void> linkAccount(@RequestParam String schoolId, @RequestBody ExamSetupService.AccountLink command,
			Authentication actor) {
		service.linkAccount(schoolId, command, actor.getName());
		return ok(null);
	}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
