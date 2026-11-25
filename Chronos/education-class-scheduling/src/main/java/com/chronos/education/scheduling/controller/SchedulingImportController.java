package com.chronos.education.scheduling.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.AcademicCatalogImportService;
import com.chronos.education.scheduling.service.SchedulingImportService;
import com.chronos.education.scheduling.service.SchedulingImportService.Batch;
import com.chronos.education.scheduling.service.SchedulingImportService.BatchPage;
import com.chronos.education.scheduling.service.SchedulingImportService.Kind;
import com.chronos.education.scheduling.service.SchedulingImportService.Preview;
import com.chronos.education.scheduling.service.SchedulingImportService.Scope;

@RestController
@RequestMapping("/admin/education/{resource:calendar-days|bell-periods|teacher-time-constraints|teaching-class-members}/import")
@PreAuthorize("""
		((#resource == 'bell-periods' or #resource == 'calendar-days') and @iamAuthorization.any(authentication,'education:term:update','education:term:manage'))
		or (#resource == 'teaching-class-members' and @iamAuthorization.any(authentication,'education:course:update','education:course:manage'))
		or (#resource == 'teacher-time-constraints' and @iamAuthorization.any(authentication,'education:scheduling:create','education:scheduling:manage'))
		""")
public class SchedulingImportController {
	private final SchedulingImportService imports;

	public SchedulingImportController(SchedulingImportService imports) {
		this.imports = imports;
	}

	@GetMapping("/template")
	public ResponseEntity<byte[]> template(@PathVariable String resource) throws IOException {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(resource + ".xlsx", StandardCharsets.UTF_8).build().toString())
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(imports.template(kind(resource)));
	}

	@PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResultData<Preview> preview(@PathVariable String resource, @RequestParam String organizationId,
			@RequestParam String termId, @RequestParam(required = false) String targetId,
			@RequestParam String templateVersion, @RequestParam MultipartFile file, Authentication authentication)
			throws IOException {
		return ok(imports.preview(kind(resource), new Scope(organizationId, termId, targetId),
				templateVersion, file, authentication));
	}

	@PostMapping("/commit")
	public ResultData<Batch> commit(@PathVariable String resource, @RequestBody CommitCommand command,
			Authentication authentication) throws IOException {
		return ok(imports.commit(kind(resource), new Scope(command.organizationId(), command.termId(), command.targetId()),
				command.previewId(), command.templateVersion(), authentication));
	}

	@GetMapping("/batches")
	public ResultData<BatchPage> batches(@PathVariable String resource, @RequestParam String organizationId,
			@RequestParam String termId, @RequestParam(required = false) String targetId,
			@RequestParam(defaultValue = "0") int page, Authentication authentication) {
		return ok(imports.batches(kind(resource), new Scope(organizationId, termId, targetId), page, authentication));
	}

	@GetMapping("/batches/{id}")
	public ResultData<Batch> batch(@PathVariable String resource, @PathVariable String id,
			@RequestParam String organizationId, @RequestParam String termId,
			@RequestParam(required = false) String targetId, Authentication authentication) throws IOException {
		return ok(imports.batch(kind(resource), new Scope(organizationId, termId, targetId), id, authentication));
	}

	@GetMapping("/batches/{id}/source")
	public ResponseEntity<byte[]> source(@PathVariable String resource, @PathVariable String id,
			@RequestParam String organizationId, @RequestParam String termId,
			@RequestParam(required = false) String targetId, Authentication authentication) throws IOException {
		var content = imports.source(kind(resource), new Scope(organizationId, termId, targetId), id, authentication);
		return AcademicCatalogImportService.xlsx(content.content(), content.filename());
	}

	private Kind kind(String resource) {
		return switch (resource) {
			case "calendar-days" -> Kind.CALENDAR_DAYS;
			case "bell-periods" -> Kind.BELL_PERIODS;
			case "teaching-class-members" -> Kind.TEACHING_MEMBERS;
			case "teacher-time-constraints" -> Kind.TEACHER_CONSTRAINTS;
			default -> throw new IllegalArgumentException("不支持的导入类型");
		};
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}

	public record CommitCommand(String organizationId, String termId, String targetId, String previewId,
			String templateVersion) {}
}
