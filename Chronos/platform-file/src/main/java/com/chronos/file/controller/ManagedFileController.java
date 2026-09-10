package com.chronos.file.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ManagedFileController {
	private final ManagedFileService service;

	public ManagedFileController(ManagedFileService service) {
		this.service = service;
	}

	@PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("isAuthenticated()")
	public ResultData<ManagedFileView> upload(
			@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "WORKFLOW_FORM_DRAFT") String businessType,
			@RequestParam(required = false) String businessId,
			Authentication authentication) {
		return ok(service.upload(file, businessType, businessId, authentication));
	}

	@GetMapping("/files/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ManagedFileView> metadata(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.metadata(id, authentication));
	}

	@GetMapping("/files/{id}/content")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<byte[]> content(
			@PathVariable String id,
			Authentication authentication) {
		var content = service.read(id, authentication);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(content.filename(), StandardCharsets.UTF_8)
						.build()
						.toString())
				.contentType(MediaType.parseMediaType(content.contentType()))
				.body(content.content());
	}

	@DeleteMapping("/files/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResultData<Void> delete(
			@PathVariable String id,
			Authentication authentication) {
		service.delete(id, authentication);
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

}
