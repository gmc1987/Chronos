package com.chronos.education.scheduling.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.AccommodationService;
import com.chronos.education.scheduling.service.AccommodationService.AssignmentCommand;
import com.chronos.education.scheduling.service.AccommodationService.BedCommand;
import com.chronos.education.scheduling.service.AccommodationService.BuildingCommand;
import com.chronos.education.scheduling.service.AccommodationService.RoomCommand;
import com.chronos.education.scheduling.service.AccommodationImportService;
import com.chronos.education.scheduling.service.AccommodationAssignmentImportService;
import com.chronos.security.DirectoryDataScopeGuard;

@RestController
@RequestMapping("/admin/education/accommodations")
public class AccommodationController {
	private final AccommodationService service;
	private final AccommodationImportService imports;
	private final AccommodationAssignmentImportService assignmentImports;
	private final DirectoryDataScopeGuard dataScopes;

	public AccommodationController(AccommodationService service, AccommodationImportService imports,
			AccommodationAssignmentImportService assignmentImports, DirectoryDataScopeGuard dataScopes) {
		this.service = service;
		this.imports = imports;
		this.assignmentImports = assignmentImports;
		this.dataScopes = dataScopes;
	}

	@GetMapping(value = "/import-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResponseEntity<byte[]> importTemplate() {
		String filename = java.net.URLEncoder.encode("Chronos-宿舍资源批量导入模板.xlsx", StandardCharsets.UTF_8);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(imports.template());
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<?> importFile(@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		String resolved = resolveOrganization(authentication, organizationId);
		return ok(imports.importFile(file, dryRun, resolved));
	}

	@GetMapping(value = "/assignment-import/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResponseEntity<byte[]> assignmentImportTemplate(@RequestParam String organizationId,
			@RequestParam String termId, Authentication authentication) {
		String resolved = resolveOrganization(authentication, organizationId);
		if (!resolved.equals(assignmentImports.termOrganization(termId)))
			throw new IllegalArgumentException("学期不存在或不属于所选机构");
		String filename = java.net.URLEncoder.encode("Chronos-住宿人工分配批量导入模板.xlsx", StandardCharsets.UTF_8);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
				.header("X-Template-Version", AccommodationAssignmentImportService.TEMPLATE_VERSION)
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(assignmentImports.template());
	}

	@PostMapping(value = "/assignment-import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<?> assignmentImportPreview(@RequestPart("file") MultipartFile file,
			@RequestParam String organizationId, @RequestParam String termId,
			@RequestParam String templateVersion, Authentication authentication) {
		String resolved = resolveOrganization(authentication, organizationId);
		return ok(assignmentImports.preview(file, resolved, termId, templateVersion, authentication.getName()));
	}

	@PostMapping("/assignment-import/commit")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<?> assignmentImportCommit(@RequestBody AssignmentImportCommit command,
			Authentication authentication) {
		String resolved = resolveOrganization(authentication, command.organizationId());
		return ok(assignmentImports.commit(command.previewId(), resolved, command.termId(), command.templateVersion(),
				command.idempotencyKey(), authentication.getName()));
	}

	@GetMapping("/assignment-import/batches/{batchId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<?> assignmentImportBatch(@PathVariable String batchId, Authentication authentication) {
		requireOrganization(authentication, assignmentImports.batchOrganization(batchId));
		return ok(assignmentImports.batch(batchId, authentication.getName()));
	}

	@GetMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> buildings(
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.buildings(selectedOrganizations(authentication, organizationId)));
	}

	@PostMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createBuilding(@RequestBody BuildingCommand command, Authentication authentication) {
		return ok(service.createBuilding(command, resolveOrganization(authentication, command.organizationId())));
	}

	@GetMapping("/buildings/{buildingId}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> rooms(@PathVariable String buildingId, Authentication authentication) {
		requireOrganization(authentication, service.buildingOrganization(buildingId)); return ok(service.rooms(buildingId));
	}

	@PostMapping("/buildings/{buildingId}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createRoom(@PathVariable String buildingId, @RequestBody RoomCommand command, Authentication authentication) {
		requireOrganization(authentication, service.buildingOrganization(buildingId)); return ok(service.createRoom(buildingId, command));
	}

	@PutMapping("/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> updateRoom(@PathVariable String roomId, @RequestBody RoomCommand command, Authentication authentication) {
		requireOrganization(authentication, service.roomOrganization(roomId)); return ok(service.updateRoom(roomId, command));
	}

	@DeleteMapping("/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Void> deleteRoom(@PathVariable String roomId, Authentication authentication) {
		requireOrganization(authentication, service.roomOrganization(roomId));
		service.deleteRoom(roomId, authentication.getName());
		return ok(null);
	}

	@PostMapping("/rooms/{roomId}/beds")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createBed(@PathVariable String roomId, @RequestBody BedCommand command, Authentication authentication) {
		requireOrganization(authentication, service.roomOrganization(roomId)); return ok(service.createBed(roomId, command));
	}

	@GetMapping("/beds")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> beds(@RequestParam String semesterCode,
			@RequestParam(defaultValue = "false") boolean availableOnly,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.beds(semesterCode, availableOnly, selectedOrganizations(authentication, organizationId)));
	}

	@GetMapping("/assignments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> assignments(@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.assignments(semesterCode, selectedOrganizations(authentication, organizationId)));
	}

	@GetMapping("/student-options")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> studentOptions(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.studentOptions(keyword, selectedOrganizations(authentication, organizationId)));
	}

	@PostMapping("/assign")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> assign(@RequestBody AssignmentCommand command, Authentication authentication) {
		String bedOrganization = service.bedOrganization(command.bedId());
		String studentOrganization = service.studentOrganization(command.studentId());
		requireOrganization(authentication, bedOrganization);
		requireOrganization(authentication, studentOrganization);
		if (!bedOrganization.equals(studentOrganization)) throw new IllegalArgumentException("学生与床位不属于同一机构");
		return ok(service.assign(command, authentication.getName(), bedOrganization));
	}

	@DeleteMapping("/assignments/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Void> cancel(@PathVariable String id, Authentication authentication) {
		requireOrganization(authentication, service.assignmentOrganization(id));
		service.cancelAssignment(id, authentication.getName());
		return ok(null);
	}
	private void requireOrganization(Authentication authentication, String organizationId) {
		dataScopes.requireOrganization(authentication, organizationId);
	}
	private String resolveOrganization(Authentication authentication, String requested) {
		return dataScopes.resolveOrganization(authentication, requested);
	}
	private java.util.Collection<String> selectedOrganizations(Authentication authentication, String requested) {
		return dataScopes.resolveQueryOrganizations(authentication, requested);
	}

	private <T> ResultData<T> ok(T data) { ResultData<T> result = new ResultData<>(); result.setCode("200"); result.setMsg("success"); result.setData(data); return result; }
	public record AssignmentImportCommit(String previewId, String organizationId, String termId,
			String templateVersion, String idempotencyKey) {}
}
