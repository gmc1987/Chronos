package com.chronos.education.scheduling.controller;

import java.io.IOException;

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
import com.chronos.education.scheduling.service.CatalogImportBatchService;
import com.chronos.education.scheduling.service.CatalogImportBatchService.Batch;
import com.chronos.education.scheduling.service.CatalogImportBatchService.Page;
import com.chronos.education.scheduling.service.CatalogImportBatchService.Scope;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping({ "/admin/education/catalog-import/{kind}", "/admin/iam/import/{kind:DIRECTORY_[A-Z_]+}/archived" })
public class CatalogImportBatchController {
	private final CatalogImportBatchService batches;

	public CatalogImportBatchController(CatalogImportBatchService batches) {
		this.batches = batches;
	}

	@PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResultData<Batch> preview(@PathVariable String kind, @RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String termCode, @RequestParam(required = false) String resourceId, @RequestParam String templateVersion,
			@RequestParam MultipartFile file, Authentication actor) throws IOException {
		return ok(batches.preview(new Scope(kind, organizationId, termCode, resourceId), templateVersion, file, actor));
	}

	@PostMapping("/commit")
	public ResultData<Batch> commit(@PathVariable String kind, @RequestBody Commit command, Authentication actor)
			throws IOException {
		return ok(batches.commit(new Scope(kind, command.organizationId(), command.termCode(), command.resourceId()),
				command.previewId(), command.templateVersion(), actor));
	}

	@GetMapping("/batches")
	public ResultData<Page> history(@PathVariable String kind, @RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String termCode, @RequestParam(required = false) String resourceId, @RequestParam(defaultValue = "0") int page,
			Authentication actor) {
		return ok(batches.history(new Scope(kind, organizationId, termCode, resourceId), page, actor));
	}

	@GetMapping("/batches/{id}")
	public ResultData<Batch> detail(@PathVariable String kind, @PathVariable String id, @RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String termCode, @RequestParam(required = false) String resourceId, Authentication actor) throws IOException {
		return ok(batches.detail(new Scope(kind, organizationId, termCode, resourceId), id, actor));
	}

	@GetMapping("/batches/{id}/source")
	public ResponseEntity<byte[]> source(@PathVariable String kind, @PathVariable String id, @RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String termCode, @RequestParam(required = false) String resourceId, Authentication actor) throws IOException {
		var source = batches.source(new Scope(kind, organizationId, termCode, resourceId), id, actor);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(source.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.contentType(MediaType.parseMediaType(source.contentType())).body(source.content());
	}

	public record Commit(String organizationId, String termCode, String resourceId, String previewId, String templateVersion) {}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
