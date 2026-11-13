package com.chronos.education.academic.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.academic.service.TermCoursePlanService;
import com.chronos.education.academic.service.TermCoursePlanService.Command;
import com.chronos.education.scheduling.service.AcademicCatalogImportService;
import com.chronos.education.scheduling.service.AcademicCatalogImportService.Kind;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/education/term-course-plans")
@RequiredArgsConstructor
public class TermCoursePlanController {
	private static final String VIEW = "hasAnyAuthority('education:term-course-plan:view','education:term-course-plan:manage')";
	private static final String MANAGE = "hasAuthority('education:term-course-plan:manage')";
	private final TermCoursePlanService plans;
	private final AcademicCatalogImportService imports;

	@GetMapping
	@PreAuthorize(VIEW)
	public ResultData<?> list(@RequestParam String organizationId, @RequestParam String termCode,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size, Authentication actor) {
		return ok(plans.list(organizationId, termCode, keyword, page, size, actor.getName()));
	}

	@GetMapping("/terms")
	@PreAuthorize(VIEW)
	public ResultData<?> terms(@RequestParam String organizationId, Authentication actor) {
		return ok(plans.terms(organizationId, actor.getName()));
	}

	@GetMapping("/options")
	@PreAuthorize(MANAGE)
	public ResultData<?> options(@RequestParam String organizationId, @RequestParam String termCode, Authentication actor) {
		return ok(plans.options(organizationId, termCode, actor.getName()));
	}

	@PostMapping
	@PreAuthorize(MANAGE)
	public ResultData<?> create(@RequestParam String organizationId, @RequestParam String termCode,
			@RequestBody Command command, Authentication actor) {
		plans.create(organizationId, termCode, command, actor.getName());
		return ok(null);
	}

	@PutMapping("/{id}")
	@PreAuthorize(MANAGE)
	public ResultData<?> update(@RequestParam String organizationId, @RequestParam String termCode,
			@PathVariable String id, @RequestBody HoursCommand command, Authentication actor) {
		plans.updateHours(organizationId, termCode, id, command.weeklyLessons(), actor.getName());
		return ok(null);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize(MANAGE)
	public ResultData<?> delete(@RequestParam String organizationId, @RequestParam String termCode,
			@PathVariable String id, Authentication actor) {
		plans.delete(organizationId, termCode, id, actor.getName());
		return ok(null);
	}

	@PostMapping("/generation")
	@PreAuthorize(MANAGE)
	public ResultData<?> generate(@RequestParam String organizationId, @RequestParam String termCode,
			@RequestBody GenerationCommand command, Authentication actor) {
		if (!command.dryRun() && (command.revisions() == null || command.revisions().isEmpty())) {
			throw new IllegalArgumentException("请先预览并确认候选教学任务");
		}
		return ok(plans.generate(organizationId, termCode, command.revisions(), command.dryRun(), actor.getName()));
	}

	@GetMapping("/import/template")
	@PreAuthorize(MANAGE)
	public ResponseEntity<byte[]> template() throws IOException {
		return AcademicCatalogImportService.xlsx(imports.template(Kind.TERM_COURSE_PLANS), "term-course-plans.xlsx");
	}

	@PostMapping("/import")
	@PreAuthorize(MANAGE)
	public ResultData<?> upload(@RequestParam String organizationId, @RequestParam String termCode,
			@RequestParam MultipartFile file, @RequestParam(defaultValue = "true") boolean dryRun,
			Authentication actor) throws IOException {
		var entries = imports.readRows(Kind.TERM_COURSE_PLANS, file);
		var errors = plans.importRows(organizationId, termCode, entries, dryRun, actor.getName());
		return ok(new AcademicCatalogImportService.Result(entries.size(), entries.size() - errors.size(), errors));
	}

	public record GenerationCommand(boolean dryRun, Map<String, String> revisions) {}
	public record HoursCommand(Integer weeklyLessons) {}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
