package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.service.GradeExcelService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/education/grades/gradebooks")
public class GradeExcelController {
	private final GradeExcelService service;

	public GradeExcelController(GradeExcelService service) {
		this.service = service;
	}

	@GetMapping("/{id}/export")
	@PreAuthorize("hasAuthority('education:score:gradebook:export')")
	public ResponseEntity<byte[]> export(@PathVariable String id, Authentication authentication) {
		byte[] content = service.export(id, authentication.getName());
		String filename = URLEncoder.encode("成绩册-" + id + ".xlsx", StandardCharsets.UTF_8);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
			.contentType(MediaType.parseMediaType(
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.body(content);
	}

	@PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('education:score:gradebook:import')")
	public ResultData<Map<String, Object>> importFile(
			@PathVariable String id,
			@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean dryRun,
			Authentication authentication) {
		return ResultData.<Map<String, Object>>builder()
			.code("200")
			.msg("ok")
			.data(service.importFile(id, file, dryRun, authentication.getName()))
			.build();
	}
}
