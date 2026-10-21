package com.chronos.education.scheduling.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.AdmissionCandidate;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.EnrollmentCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.RegistrationCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.ReviewCommand;
import com.chronos.education.scheduling.service.AdmissionService;
import com.chronos.education.scheduling.service.AdmissionImportService;

@RestController
@RequestMapping("/admin/education/admissions")
public class AdmissionController {
	private final AdmissionService service;
	private final AdmissionImportService imports;
	public AdmissionController(AdmissionService service, AdmissionImportService imports) { this.service = service; this.imports = imports; }

	@GetMapping(value = "/import-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:import','education:admission:manage')")
	public ResponseEntity<byte[]> importTemplate() {
		String filename = java.net.URLEncoder.encode("Chronos-正式录取名册模板.xlsx", StandardCharsets.UTF_8);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(imports.template());
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:import','education:admission:manage')")
	public ResultData<?> importFile(@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun) {
		return ok(imports.importFile(file, dryRun));
	}

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:view','education:admission:manage')")
	public ResultData<?> list(@RequestParam(defaultValue = "") String status,
			@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ok(PageView.from(service.list(status, keyword, page, size)));
	}

	@PostMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:import','education:admission:manage')")
	public ResultData<AdmissionCandidate> create(@RequestBody AdmissionCandidate value) {
		value.setId(null); return ok(service.save(value));
	}

	@PutMapping("/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:import','education:admission:manage')")
	public ResultData<AdmissionCandidate> update(@PathVariable String id, @RequestBody AdmissionCandidate value) {
		value.setId(id); return ok(service.save(value));
	}

	@PostMapping("/{id}/review")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:review','education:admission:manage')")
	public ResultData<AdmissionCandidate> review(@PathVariable String id, @RequestBody ReviewCommand command,
			Authentication authentication) {
		return ok(service.review(id, command, authentication.getName()));
	}

	@PostMapping("/{id}/enroll")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:enroll','education:admission:manage')")
	public ResultData<AdmissionCandidate> enroll(@PathVariable String id, @RequestBody EnrollmentCommand command) {
		return ok(service.enroll(id, command));
	}

	@PostMapping("/{id}/registration")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:register','education:admission:manage')")
	public ResultData<AdmissionCandidate> registration(@PathVariable String id, @RequestBody RegistrationCommand command) {
		return ok(service.updateRegistration(id, command));
	}

	private <T> ResultData<T> ok(T data) { ResultData<T> result = new ResultData<>(); result.setCode("200"); result.setMsg("success"); result.setData(data); return result; }
}
